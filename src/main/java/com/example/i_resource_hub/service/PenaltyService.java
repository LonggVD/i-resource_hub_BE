package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.PenaltyRequest;
import com.example.i_resource_hub.dto.response.EvidenceResponse;
import com.example.i_resource_hub.dto.response.PenaltyResponse;
import com.example.i_resource_hub.entity.*;
import com.example.i_resource_hub.repository.BookingEvidenceRepository;
import com.example.i_resource_hub.repository.BookingRepository;
import com.example.i_resource_hub.repository.PenaltyRepository;
import com.example.i_resource_hub.repository.UserRepository;
import com.example.i_resource_hub.security.AuthorizationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PenaltyService {

    private final PenaltyRepository penaltyRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingEvidenceRepository bookingEvidenceRepository;
    private final NotificationService notificationService;
    private final AuthorizationHelper authHelper;

    /**
     * Tạo penalty do hệ thống tự sinh (BookingCleanupTask, checkOut khi DAMAGE...).
     * Khác với createPenalty: không cần adminUserId, createdByUser=null → fallback "Hệ thống" trong toResponse.
     * Idempotent: nếu đã tồn tại penalty cùng booking + cùng type còn ACTIVE thì bỏ qua.
     *
     * @return Penalty vừa tạo, hoặc null nếu skip do đã tồn tại / dữ liệu thiếu.
     */
    @Transactional
    public Penalty createSystemPenalty(User user, Booking booking, String penaltyType,
                                       int penaltyPoint, String description) {
        if (user == null || penaltyType == null || penaltyType.isBlank()) {
            return null;
        }

        // Idempotent: nếu booking đã từng có penalty cùng type (bất kể ACTIVE / REVOKED) thì skip.
        // Tránh trường hợp admin ân xá rồi cron lại phạt lần 2.
        if (booking != null
                && penaltyRepository.existsByBooking_IdAndPenaltyTypeAndIsDeletedFalse(
                        booking.getId(), penaltyType)) {
            log.debug("Skip createSystemPenalty: booking {} đã có penalty {} (active hoặc đã revoked)",
                    booking.getId(), penaltyType);
            return null;
        }

        // Chụp id trước khi deductCreditScore (clearAutomatically=true) detach proxy.
        String userId = user.getId();

        Penalty penalty = Penalty.builder()
                .user(user)
                .booking(booking)
                .penaltyType(penaltyType)
                .penaltyPoint(penaltyPoint)
                .description(description)
                .status("ACTIVE")
                .createdByUser(null) // Hệ thống
                .requiresReview(false)
                .build();
        penaltyRepository.save(penalty);

        // Atomic UPDATE trừ điểm tín nhiệm — tránh race condition khi nhiều penalty cùng tạo song song.
        // Đồng thời tự động set LOCKED nếu score về 0 (xử lý trong native query).
        userRepository.deductCreditScore(userId, penaltyPoint);

        // Sau @Modifying(clearAutomatically=true), persistence context bị clear → proxy `user` gốc
        // bị detach (LazyInitializationException nếu gọi setter/getter chưa khởi tạo).
        // Reload managed entity và dùng nó cho mọi thao tác phía sau.
        User refreshed = userRepository.findById(userId).orElseThrow();

        log.info("Auto-penalty created: user={}, booking={}, type={}, point=-{}, newScore={}",
                refreshed.getUsername(),
                booking != null ? booking.getId() : "(none)",
                penaltyType, penaltyPoint, refreshed.getCreditScore());

        // Notify user
        try {
            String title;
            String body;
            if ("OVERDUE".equals(penaltyType)) {
                title = "Bạn bị phạt do trễ trả thiết bị";
                body = "Bị trừ " + penaltyPoint + " điểm tín nhiệm. Điểm hiện tại: "
                        + refreshed.getCreditScore() + ". Hãy trả thiết bị đúng giờ ở các lần sau.";
            } else if ("DAMAGE".equals(penaltyType)) {
                title = "Bạn bị phạt do hư hỏng thiết bị";
                body = "Bị trừ " + penaltyPoint + " điểm tín nhiệm. Điểm hiện tại: "
                        + refreshed.getCreditScore() + ". Vui lòng kiểm tra thiết bị kỹ trước khi sử dụng.";
            } else if ("NO_SHOW".equals(penaltyType)) {
                title = "Bạn bị phạt do không đến nhận thiết bị";
                body = "Bị trừ " + penaltyPoint + " điểm tín nhiệm. Điểm hiện tại: "
                        + refreshed.getCreditScore() + ". Lần sau nếu không cần mượn nữa, hãy chủ động huỷ đơn để bạn khác có thể dùng.";
            } else {
                title = "Bạn nhận một án phạt mới";
                body = description;
            }
            notificationService.createAndPush(refreshed, "PENALTY_CREATED", penalty.getId(), title, body);
        } catch (Exception e) {
            log.warn("Không gửi được notification cho penalty {}: {}", penalty.getId(), e.getMessage());
        }

        return penalty;
    }

    /**
     * Tạo án phạt mới + trừ điểm tín nhiệm + khóa tài khoản nếu cần
     */
    @Transactional
    public PenaltyResponse createPenalty(PenaltyRequest request, String adminUserId) {
        User targetUser = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // RBAC theo unit: manager chỉ phạt SV trong khoa mình. Admin được phép cross-unit.
        authHelper.requireSameUnitOrAdmin(
                targetUser.getUnit() != null ? targetUser.getUnit().getId() : null,
                "sinh viên " + targetUser.getFullName());

        User adminUser = userRepository.findByUsername(adminUserId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quản trị viên"));

        Booking booking = null;
        if (request.getBookingId() != null && !request.getBookingId().isBlank()) {
            booking = bookingRepository.findById(request.getBookingId()).orElse(null);
        }

        // 1. Tạo bản ghi Penalty
        Penalty penalty = Penalty.builder()
                .user(targetUser)
                .booking(booking)
                .penaltyType(request.getPenaltyType())
                .penaltyPoint(request.getPenaltyPoint())
                .description(request.getDescription())
                .fineAmount(request.getFineAmount())
                .requiresReview(request.getRequiresReview() != null ? request.getRequiresReview() : false)
                .reviewStatus(Boolean.TRUE.equals(request.getRequiresReview()) ? "PENDING" : null)
                .status("ACTIVE")
                .createdByUser(adminUser)
                .build();
        penaltyRepository.save(penalty);

        // 2. Lưu minh chứng riêng cho án phạt (nếu có)
        if (request.getEvidenceUrls() != null && !request.getEvidenceUrls().isEmpty()) {
            java.util.List<com.example.i_resource_hub.entity.PenaltyEvidence> evidences = request.getEvidenceUrls()
                    .stream()
                    .map(url -> com.example.i_resource_hub.entity.PenaltyEvidence.builder()
                            .penalty(penalty)
                            .imageUrl(url)
                            .build())
                    .collect(Collectors.toList());
            penalty.setEvidences(evidences);
            penaltyRepository.save(penalty);
        }

        // 3. Atomic UPDATE: trừ điểm tín nhiệm + lock account nếu score về 0.
        // Tránh race condition khi 2 admin tạo penalty cho cùng SV đồng thời.
        userRepository.deductCreditScore(targetUser.getId(), request.getPenaltyPoint());
        // Reload state mới
        User refreshed = userRepository.findById(targetUser.getId()).orElse(targetUser);
        targetUser.setCreditScore(refreshed.getCreditScore());
        targetUser.setStatus(refreshed.getStatus());

        // 5. Gửi notification cho sinh viên
        try {
            String title = "Bạn bị xử phạt: " + request.getPenaltyType();
            String body = "Bị trừ " + request.getPenaltyPoint() + " điểm tín nhiệm. Điểm hiện tại: "
                    + targetUser.getCreditScore() + ". Lý do: "
                    + (request.getDescription() != null ? request.getDescription() : "(không có)");
            notificationService.createAndPush(
                    targetUser, "PENALTY_CREATED", penalty.getId(), title, body);
        } catch (Exception e) {
            log.warn("Không gửi được notification cho penalty {}: {}", penalty.getId(), e.getMessage());
        }

        return toResponse(penalty);
    }

    /**
     * Lấy chi tiết một án phạt
     */
    @Transactional(readOnly = true)
    public PenaltyResponse getPenaltyById(String penaltyId) {
        Penalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy án phạt"));
        authHelper.requireSameUnitOrAdmin(
                penalty.getUser() != null && penalty.getUser().getUnit() != null
                        ? penalty.getUser().getUnit().getId() : null,
                "án phạt #" + penaltyId);
        return toResponse(penalty);
    }

    /**
     * Lấy án phạt theo phạm vi RBAC:
     *  - Admin: toàn hệ thống.
     *  - Manager/giáo vụ: chỉ SV thuộc unit của mình.
     */
    @Transactional(readOnly = true)
    public List<PenaltyResponse> getAllPenalties() {
        String unitId = authHelper.getScopedUnitIdOrNull();
        return penaltyRepository.findByUnitScope(unitId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy án phạt theo userId (SV xem của mình)
     */
    @Transactional(readOnly = true)
    public List<PenaltyResponse> getPenaltiesByUser(String userId) {
        return penaltyRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Thu hồi án phạt (Admin sửa sai / ân xá)
     */
    @Transactional
    public PenaltyResponse revokePenalty(String penaltyId) {
        Penalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy án phạt"));

        // Manager chỉ ân xá penalty của SV trong khoa mình.
        authHelper.requireSameUnitOrAdmin(
                penalty.getUser() != null && penalty.getUser().getUnit() != null
                        ? penalty.getUser().getUnit().getId() : null,
                "án phạt #" + penaltyId);

        if (!"ACTIVE".equals(penalty.getStatus())) {
            throw new RuntimeException("Án phạt này đã bị thu hồi trước đó");
        }

        // 1. Đánh dấu thu hồi
        penalty.setStatus("REVOKED");
        penaltyRepository.save(penalty);

        // 2 + 3. Atomic UPDATE: hoàn điểm tín nhiệm (giới hạn 100) + tự động unlock account nếu score > 0.
        User user = penalty.getUser();
        boolean wasLocked = "LOCKED".equals(user.getStatus());
        String userId = user.getId();
        int restorePoints = penalty.getPenaltyPoint();
        userRepository.restoreCreditScore(userId, restorePoints);

        // Sau khi @Modifying(clearAutomatically=true) chạy, persistence context bị clear
        // → toàn bộ proxy (kể cả penalty.booking) bị detach. Reload lại penalty + user để dùng tiếp.
        penalty = penaltyRepository.findById(penaltyId).orElseThrow();
        User refreshed = userRepository.findById(userId).orElseThrow();
        user = refreshed;
        boolean wasUnlocked = wasLocked && "ACTIVE".equals(user.getStatus());

        // 4. Notify sinh viên — án phạt đã được thu hồi
        try {
            String title = "Án phạt đã được thu hồi";
            StringBuilder body = new StringBuilder();
            body.append("Án phạt ").append(penalty.getPenaltyType())
                    .append(" của bạn đã được ân xá. Hoàn lại ")
                    .append(penalty.getPenaltyPoint()).append(" điểm tín nhiệm. Điểm hiện tại: ")
                    .append(user.getCreditScore()).append(".");
            if (wasUnlocked) {
                body.append(" Tài khoản đã được mở khoá lại.");
            }
            notificationService.createAndPush(
                    user, "PENALTY_REVOKED", penalty.getId(), title, body.toString());
        } catch (Exception e) {
            log.warn("Không gửi được notification revoke penalty {}: {}",
                    penalty.getId(), e.getMessage());
        }

        return toResponse(penalty);
    }

    /**
     * Xóa mềm án phạt
     */
    @Transactional
    public void deletePenalty(String penaltyId) {
        Penalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy án phạt"));
        authHelper.requireSameUnitOrAdmin(
                penalty.getUser() != null && penalty.getUser().getUnit() != null
                        ? penalty.getUser().getUnit().getId() : null,
                "án phạt #" + penaltyId);
        penalty.setDeleted(true);
        penaltyRepository.save(penalty);
    }

    /**
     * Chuyển Entity → DTO
     */
    private PenaltyResponse toResponse(Penalty p) {
        User user = p.getUser();
        long activePenalties = penaltyRepository.countByUserIdAndStatusAndIsDeletedFalse(user.getId(), "ACTIVE");

        List<EvidenceResponse> evidenceList = new ArrayList<>();
        
        // 1. Tìm minh chứng (Truy quét rộng: theo Booking, Batch hoặc ResourceItem)
        if (p.getBooking() != null) {
            String bookingId = p.getBooking().getId();
            String batchToken = p.getBooking().getBatchToken();
            String resourceItemId = (p.getBooking().getResourceItem() != null) ? p.getBooking().getResourceItem().getId() : null;

            // Lấy tất cả minh chứng liên quan đến đơn mượn này hoặc lô này
            List<BookingEvidence> bEvidences;
            if (batchToken != null && !batchToken.isBlank()) {
                // Nếu mượn theo lô, lấy tất cả minh chứng của các đơn trong lô đó
                bEvidences = bookingEvidenceRepository.findAllByBookingBatchToken(batchToken);
            } else {
                bEvidences = bookingEvidenceRepository.findByBookingIdAndIsDeletedFalseOrderByCreatedAtDesc(bookingId);
            }
            
            // Nếu vẫn rỗng, thử tìm theo ResourceItem cụ thể
            if (bEvidences.isEmpty() && resourceItemId != null) {
                bEvidences = bookingEvidenceRepository.findByResourceItemIdAndIsDeletedFalseOrderByCreatedAtDesc(resourceItemId);
            }

            bEvidences.stream()
                    .map(this::mapToEvidenceResponse)
                    .forEach(evidenceList::add);
        }

        // 2. Lấy minh chứng riêng của Án phạt
        if (p.getEvidences() != null && !p.getEvidences().isEmpty()) {
            p.getEvidences().stream()
                    .map(ev -> mapToEvidenceResponse(ev, p))
                    .forEach(evidenceList::add);
        }

        // 3. Xác định Ảnh đại diện (Thumbnail)
        String thumb = null;
        if (!evidenceList.isEmpty()) {
            // Ưu tiên ảnh minh chứng thực tế
            thumb = evidenceList.get(0).getImageUrl();
            if (thumb != null && thumb.contains(",")) thumb = thumb.split(",")[0];
        } 
        
        // Fallback: Nếu không có ảnh minh chứng, lấy ảnh của mẫu thiết bị (giống My Bookings)
        if (thumb == null && p.getBooking() != null && p.getBooking().getResourceItem() != null 
            && p.getBooking().getResourceItem().getTemplate() != null) {
            thumb = p.getBooking().getResourceItem().getTemplate().getImageUrl();
        }

        return PenaltyResponse.builder()
                .id(p.getId())
                .userId(user.getId())
                .studentCode(user.getStudentCode())
                .studentName(user.getFullName())
                .bookingId(p.getBooking() != null ? p.getBooking().getId() : null)
                .penaltyType(p.getPenaltyType())
                .penaltyPoint(p.getPenaltyPoint())
                .description(p.getDescription())
                .status(p.getStatus())
                .createdByName(p.getCreatedByUser() != null ? p.getCreatedByUser().getFullName() : "Hệ thống")
                .createdAt(p.getCreatedAt())
                .currentCreditScore(user.getCreditScore())
                .userStatus(user.getStatus())
                .totalActivePenalties(activePenalties)
                .evidences(evidenceList)
                .fineAmount(p.getFineAmount())
                .requiresReview(p.getRequiresReview())
                .reviewStatus(p.getReviewStatus())
                .bookingBatchToken(p.getBooking() != null ? p.getBooking().getBatchToken() : null)
                .bookingDate(p.getBooking() != null ? p.getBooking().getBookingDate() : null)
                .bookingSlot(p.getBooking() != null && p.getBooking().getSlot() != null
                        ? p.getBooking().getSlot().getSlotName()
                        : null)
                .bookingDeviceName(p.getBooking() != null && p.getBooking().getResourceItem() != null
                        && p.getBooking().getResourceItem().getTemplate() != null
                                ? p.getBooking().getResourceItem().getTemplate().getName()
                                : null)
                .thumbnailUrl(thumb)
                .build();
    }

    private EvidenceResponse mapToEvidenceResponse(BookingEvidence e) {
        Booking booking = e.getBooking();
        String borrowerName = null;
        String borrowerId = null;
        String userId = null;
        String serialNumber = null;
        String deviceName = null;
        String ownerUnitName = null;

        if (booking != null) {
            if (booking.getUser() != null) {
                borrowerName = booking.getUser().getFullName();
                borrowerId = booking.getUser().getStudentCode();
                userId = booking.getUser().getId();
            }
            if (booking.getResourceItem() != null) {
                serialNumber = booking.getResourceItem().getSerialNumber();
                if (booking.getResourceItem().getTemplate() != null) {
                    deviceName = booking.getResourceItem().getTemplate().getName();
                }
            }
            if (booking.getManagedByUnit() != null) {
                ownerUnitName = booking.getManagedByUnit().getUnitName();
            }
        }

        return EvidenceResponse.builder()
                .id(e.getId())
                .bookingId(booking != null ? booking.getId() : null)
                .resourceItemId(e.getResourceItem() != null ? e.getResourceItem().getId() : null)
                .evidenceType(e.getEvidenceType())
                .imageUrl(e.getImageUrl())
                .description(e.getDescription())
                .resolution(e.getResolution())
                .isResolved(e.getIsResolved())
                .createdBy(e.getCreatedBy() != null ? e.getCreatedBy().getFullName() : "Hệ thống")
                .createdAt(e.getCreatedAt())
                .borrowerName(borrowerName)
                .borrowerId(borrowerId)
                .userId(userId)
                .serialNumber(serialNumber)
                .deviceName(deviceName)
                .ownerUnitName(ownerUnitName)
                .build();
    }

    private EvidenceResponse mapToEvidenceResponse(PenaltyEvidence e, Penalty p) {
        User user = p.getUser();
        Booking booking = p.getBooking();
        
        return EvidenceResponse.builder()
                .id(e.getId())
                .evidenceType("PENALTY_PROOF")
                .imageUrl(e.getImageUrl())
                .description("Minh chứng đi kèm án phạt: " + p.getPenaltyType())
                .createdBy(p.getCreatedByUser() != null ? p.getCreatedByUser().getFullName() : "Hệ thống")
                .createdAt(e.getCreatedAt() != null ? e.getCreatedAt() : p.getCreatedAt())
                .borrowerName(user.getFullName())
                .borrowerId(user.getStudentCode())
                .userId(user.getId())
                .bookingId(booking != null ? booking.getId() : null)
                .deviceName(booking != null && booking.getResourceItem() != null && booking.getResourceItem().getTemplate() != null 
                            ? booking.getResourceItem().getTemplate().getName() : null)
                .serialNumber(booking != null && booking.getResourceItem() != null ? booking.getResourceItem().getSerialNumber() : null)
                .build();
    }
}
