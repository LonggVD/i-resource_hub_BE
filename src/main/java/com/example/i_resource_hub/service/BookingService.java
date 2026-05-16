package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.ActionRequest;
import com.example.i_resource_hub.dto.request.BookingRequest;
import com.example.i_resource_hub.dto.request.BulkBookingRequest;
import com.example.i_resource_hub.dto.request.BulkCheckInRequest;
import com.example.i_resource_hub.dto.request.EvidenceRequest;
import com.example.i_resource_hub.dto.response.BookingResponse;
import com.example.i_resource_hub.entity.*;
import com.example.i_resource_hub.repository.*;
import com.example.i_resource_hub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingHistoryRepository historyRepository;
    private final BookingEvidenceRepository evidenceRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final PenaltyService penaltyService;
    private final NotificationService notificationService;

    @Value("${penalty.points.late-return:10}")
    private int latePenaltyPoint;

    @Value("${penalty.points.damage:30}")
    private int damagePenaltyPoint;

    @Value("${penalty.points.no-show:5}")
    private int noShowPenaltyPoint;

    @Value("${penalty.late-return.grace-minutes:30}")
    private int lateReturnGraceMinutes;

    /**
     * Lấy toàn bộ danh sách đơn mượn (cho bảng Kanban)
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Lấy danh sách đơn mượn của riêng Sinh viên hiện tại
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings() {
        User currentUser = getCurrentUser();
        return bookingRepository.findByUser_IdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Luồng 1: Sinh viên Đặt lịch
     */
    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        User currentUser = getCurrentUser();

        // 1. Tìm tất cả thiết bị khả dụng
        TimeSlot slot = timeSlotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new RuntimeException("Khung giờ không hợp lệ"));

        if (request.getBookingDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("Không thể đặt lịch cho ngày trong quá khứ!");
        }

        if (request.getBookingDate().equals(LocalDate.now())) {
            if (slot.getEndTime().isBefore(java.time.LocalTime.now())) {
                throw new RuntimeException("Khung giờ này đã kết thúc, vui lòng chọn ca khác!");
            }
        }

        // 1. Tìm thiết bị khả dụng kèm khoá pessimistic để tránh race condition
        List<ResourceItem> availableItems = resourceItemRepository.findAvailableItemsForUpdate(
                request.getResourceTemplateId(),
                request.getBookingDate(),
                slot.getStartTime(),
                slot.getEndTime());

        int requestedQty = (request.getQuantity() != null && request.getQuantity() > 0) ? request.getQuantity() : 1;

        if (availableItems.size() < requestedQty) {
            throw new RuntimeException("Chỉ còn " + availableItems.size() + " thiết bị khả dụng. Không đủ số lượng "
                    + requestedQty + " bạn yêu cầu!");
        }

        // 2. Tạo danh sách Booking
        Booking lastSavedBooking = null;
        String batchToken = UUID.randomUUID().toString();

        for (int i = 0; i < requestedQty; i++) {
            ResourceItem item = availableItems.get(i);

            // Tự động xác định đơn vị quản lý (Khoa)
            OrganizationUnit managedByUnit = item.getManagedByUnit() != null ? item.getManagedByUnit()
                    : (item.getTemplate() != null ? item.getTemplate().getUnit() : null);

            Booking booking = Booking.builder()
                    .user(currentUser)
                    .resourceItem(item)
                    .managedByUnit(managedByUnit)
                    .batchToken(batchToken)
                    .bookingDate(request.getBookingDate())
                    .slot(slot)
                    .purpose(request.getPurpose())
                    .status("PENDING")
                    .qrCodeToken(UUID.randomUUID().toString())
                    .build();

            if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
                Set<User> participants = new HashSet<>(userRepository.findAllById(request.getParticipantIds()));
                booking.setParticipants(participants);
            }

            lastSavedBooking = bookingRepository.save(booking);
            saveHistory(lastSavedBooking, null, "PENDING", currentUser,
                    "Tạo đơn đăng ký mới (Số lượng: " + requestedQty + ")");
        }

        // Notify Manager của Khoa quản lý — gửi 1 lần cho cả batch
        if (lastSavedBooking != null) {
            String borrowerName = currentUser.getFullName() != null
                    ? currentUser.getFullName() : currentUser.getUsername();
            notifyManagersOfBooking(lastSavedBooking, "BOOKING_CREATED",
                    "Có đơn mượn mới chờ duyệt",
                    borrowerName + " vừa đăng ký mượn " + describeBooking(lastSavedBooking)
                            + (requestedQty > 1 ? " (số lượng: " + requestedQty + ")" : "")
                            + ". Vào bảng Kanban để duyệt.");
        }

        return mapToResponse(lastSavedBooking);
    }

    /**
     * Luồng mượn nhiều loại đồ khác nhau cùng lúc (Batch/Bulk Booking)
     */
    @Transactional
    public void createBulkBookings(BulkBookingRequest request) {
        User currentUser = getCurrentUser();
        String batchToken = UUID.randomUUID().toString(); // Mã định danh chung cho cả đợt đăng ký này
        Map<String, Booking> oneBookingPerUnit = new HashMap<>(); // gom 1 noti / Khoa

        // Duyệt qua từng loại đồ trong danh sách và tạo đơn
        for (BulkBookingRequest.BookingItemRequest itemReq : request.getItems()) {
            TimeSlot slot = timeSlotRepository.findById(itemReq.getSlotId())
                    .orElseThrow(() -> new RuntimeException(
                            "Khung giờ không hợp lệ cho món đồ có ID: " + itemReq.getResourceTemplateId()));

            if (itemReq.getBookingDate().isBefore(LocalDate.now())) {
                throw new RuntimeException("Ngày mượn không hợp lệ cho một số món đồ!");
            }

            List<ResourceItem> availableItems = resourceItemRepository.findAvailableItemsForUpdate(
                    itemReq.getResourceTemplateId(),
                    itemReq.getBookingDate(),
                    slot.getStartTime(),
                    slot.getEndTime());

            int requestedQty = (itemReq.getQuantity() != null && itemReq.getQuantity() > 0) ? itemReq.getQuantity() : 1;

            if (availableItems.size() < requestedQty) {
                throw new RuntimeException(
                        "Thiết bị mượn không đủ số lượng cho món: " + itemReq.getResourceTemplateId());
            }

            // Với mỗi món đồ cụ thể, xác định khoa quản lý và tạo Booking
            for (int i = 0; i < requestedQty; i++) {
                ResourceItem item = availableItems.get(i);

                // Xác định khoa quản lý (Ưu tiên từ Item -> Template)
                OrganizationUnit managedByUnit = item.getManagedByUnit() != null ? item.getManagedByUnit()
                        : (item.getTemplate() != null ? item.getTemplate().getUnit() : null);

                Booking booking = Booking.builder()
                        .user(currentUser)
                        .resourceItem(item)
                        .managedByUnit(managedByUnit)
                        .bookingDate(itemReq.getBookingDate())
                        .slot(slot)
                        .purpose(request.getPurpose())
                        .status("PENDING")
                        .qrCodeToken(UUID.randomUUID().toString())
                        .batchToken(batchToken)
                        .build();

                Booking saved = bookingRepository.save(booking);
                saveHistory(saved, null, "PENDING", currentUser, "Tạo đơn mượn trong danh sách (Bulk)");

                // Gom 1 booking đại diện cho mỗi Khoa, để cuối hàm gửi 1 noti / Khoa
                if (managedByUnit != null) {
                    oneBookingPerUnit.putIfAbsent(managedByUnit.getId(), saved);
                }
            }
        }

        // Notify Manager: 1 thông báo cho mỗi Khoa có đơn trong batch
        String borrowerName = currentUser.getFullName() != null
                ? currentUser.getFullName() : currentUser.getUsername();
        for (Booking representative : oneBookingPerUnit.values()) {
            notifyManagersOfBooking(representative, "BOOKING_CREATED",
                    "Có đơn mượn mới chờ duyệt",
                    borrowerName + " vừa đăng ký một lô đơn mượn. Vào bảng Kanban để duyệt.");
        }
    }

    /**
     * Kiểm tra số lượng thiết bị khả dụng cho một mẫu, ngày và khung giờ cụ thể
     */
    @Transactional(readOnly = true)
    public int getAvailableQuantity(String templateId, LocalDate date, String slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Khung giờ không hợp lệ!"));

        List<ResourceItem> availableItems = resourceItemRepository.findAvailableItems(
                templateId,
                date,
                slot.getStartTime(),
                slot.getEndTime());

        return availableItems.size();
    }

    /**
     * Map Entity sang DTO để Frontend dễ dùng
     */
    private BookingResponse mapToResponse(Booking booking) {
        // Tìm minh chứng hư hỏng (nếu có)
        Optional<BookingEvidence> damageEvidence = booking.getEvidences().stream()
                .filter(e -> "DAMAGE".equalsIgnoreCase(e.getEvidenceType()))
                .findFirst();

        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUser() != null ? booking.getUser().getId() : null)
                .borrowerName(booking.getUser() != null ? booking.getUser().getFullName() : "N/A")
                .borrowerId(booking.getUser() != null ? booking.getUser().getStudentCode() : "N/A")
                .borrowerUnitName(booking.getUser() != null && booking.getUser().getUnit() != null
                        ? booking.getUser().getUnit().getUnitName()
                        : "Tự do")
                .resourceItemId(isItemBound(booking.getStatus()) && booking.getResourceItem() != null
                        ? booking.getResourceItem().getId() : null)
                .deviceName(booking.getResourceItem() != null && booking.getResourceItem().getTemplate() != null
                        ? booking.getResourceItem().getTemplate().getName()
                        : "Thiết bị không tên")
                .serialNumber(isItemBound(booking.getStatus()) && booking.getResourceItem() != null
                        ? booking.getResourceItem().getSerialNumber() : "")
                .bookingDate(booking.getBookingDate())
                .slotId(booking.getSlot() != null ? booking.getSlot().getId() : null)
                .slotName(booking.getSlot() != null ? booking.getSlot().getSlotName() : "")
                .startTime(booking.getSlot() != null ? booking.getSlot().getStartTime().toString() : "")
                .endTime(booking.getSlot() != null ? booking.getSlot().getEndTime().toString() : "")
                .status(booking.getStatus())
                .actualStartTime(booking.getActualStartTime())
                .actualEndTime(booking.getActualEndTime())
                .qrCodeToken(booking.getQrCodeToken())
                .purpose(booking.getPurpose())
                .batchToken(booking.getBatchToken())
                .expired(isExpired(booking))
                .ownerUnitId(booking.getManagedByUnit() != null ? booking.getManagedByUnit().getId() : null)
                .ownerUnitName(
                        booking.getManagedByUnit() != null ? booking.getManagedByUnit().getUnitName() : "Khoa/Đơn vị")
                .hasDamage(damageEvidence.isPresent())
                .damageDescription(damageEvidence.map(BookingEvidence::getDescription).orElse(null))
                .resolution(damageEvidence.map(BookingEvidence::getResolution).orElse(null))
                .isResolved(damageEvidence.map(BookingEvidence::getIsResolved).orElse(false))
                .evidenceImageUrl(damageEvidence.map(BookingEvidence::getImageUrl).orElse(null))
                .isPenalized(booking.getPenalties().stream().anyMatch(p -> "ACTIVE".equals(p.getStatus()) && !p.isDeleted()))
                .build();
    }

    /**
     * Lấy danh sách đơn mượn theo Đơn vị của User hiện tại (cho bảng Kanban)
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getKanbanBookings() {
        User currentUser = getCurrentUser();

        log.debug("User {} accessing Kanban. Unit: {}", currentUser.getUsername(),
                currentUser.getUnit() != null ? currentUser.getUnit().getUnitName() : "NULL");

        // Nếu không thuộc đơn vị nào, coi như không thấy gì (hoặc thấy hết tùy logic hệ
        // thống)
        if (currentUser.getUnit() == null) {
            return List.of();
        }

        return bookingRepository.findAllByUnitId(currentUser.getUnit().getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Luồng 2: Giáo vụ Duyệt / Từ chối (Lọc theo Row-Level Security)
     */
    @Transactional
    public void processAction(String bookingId, ActionRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn đặt lịch"));

        processSingleAction(booking, request);
    }

    @Transactional
    public void processBulkAction(List<String> bookingIds, ActionRequest request) {
        User currentUser = getCurrentUser();
        List<Booking> bookings = bookingRepository.findAllById(bookingIds);

        for (Booking booking : bookings) {
            // Tận dụng logic kiểm tra quyền của từng đơn
            OrganizationUnit adminUnit = currentUser.getUnit();
            OrganizationUnit effectiveUnit = getEffectiveUnit(booking);

            if (adminUnit == null || effectiveUnit == null || !adminUnit.getId().equals(effectiveUnit.getId())) {
                continue; // Bỏ qua nếu không có quyền (an toàn hơn throw error giữa chừng)
            }

            processSingleAction(booking, request);
        }
    }

    private void processSingleAction(Booking booking, ActionRequest request) {
        User currentUser = getCurrentUser();
        String oldStatus = booking.getStatus();

        if (!"PENDING".equalsIgnoreCase(oldStatus)) {
            return; // Chỉ xử lý đơn PENDING
        }

        // Kiểm tra hết hạn: Nếu giờ kết thúc của ca < hiện tại thì tự hủy
        if (isExpired(booking)) {
            booking.setStatus("CANCELLED");
            booking.setCancelledReason("Hệ thống tự động hủy do quá giờ phê duyệt của ca mượn");
            booking.setCancelledAt(LocalDateTime.now());
            bookingRepository.save(booking);
            saveHistory(booking, oldStatus, "CANCELLED", null, "Tự động hủy do quá giờ");
            throw new RuntimeException("Đơn mượn này đã hết hạn ca mượn, không thể duyệt nữa!");
        }

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            booking.setStatus("APPROVED");
            booking.setApprovedAt(LocalDateTime.now());
            booking.setApprovedBy(currentUser);
            saveHistory(booking, oldStatus, "APPROVED", currentUser, "Đã phê duyệt đơn (Batch)");
            notifyBookingActor(booking, "BOOKING_APPROVED",
                    "Đơn mượn của bạn đã được duyệt",
                    "Đơn mượn " + describeBooking(booking) + " đã được duyệt. Bạn có thể đến nhận thiết bị đúng ca.");
        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            booking.setStatus("REJECTED");
            booking.setRejectedReason(request.getReason());
            saveHistory(booking, oldStatus, "REJECTED", currentUser, "Từ chối đơn (Batch): " + request.getReason());
            notifyBookingActor(booking, "BOOKING_REJECTED",
                    "Đơn mượn của bạn đã bị từ chối",
                    "Đơn " + describeBooking(booking) + " bị từ chối. Lý do: "
                            + (request.getReason() != null ? request.getReason() : "(không có)"));
        }

        bookingRepository.save(booking);
    }

    private String describeBooking(Booking booking) {
        String device = "thiết bị";
        if (booking.getResourceItem() != null && booking.getResourceItem().getTemplate() != null) {
            device = booking.getResourceItem().getTemplate().getName();
        }
        String slotName = booking.getSlot() != null ? booking.getSlot().getSlotName() : "";
        return device + " (" + slotName + " ngày " + booking.getBookingDate() + ")";
    }

    private void notifyBookingActor(Booking booking, String type, String title, String content) {
        if (booking == null || booking.getUser() == null) return;
        try {
            notificationService.createAndPush(
                    booking.getUser(), type, booking.getId(), title, content);
        } catch (Exception e) {
            log.warn("Không thể gửi notification cho booking {}: {}", booking.getId(), e.getMessage());
        }
    }

    /**
     * Notify tất cả Manager (+ Admin) của Khoa quản lý thiết bị về sự kiện liên quan đơn mượn.
     */
    private void notifyManagersOfBooking(Booking booking, String type, String title, String content) {
        if (booking == null) return;
        OrganizationUnit unit = getEffectiveUnit(booking);
        if (unit == null) {
            log.debug("Booking {} không có managedByUnit → bỏ qua notify manager", booking.getId());
            return;
        }
        try {
            List<User> recipients = userRepository.findManagersAndAdminsByUnitId(unit.getId());
            for (User m : recipients) {
                // Bỏ qua nếu manager chính là người tạo (trường hợp manager tự đặt cho mình)
                if (booking.getUser() != null && m.getId().equals(booking.getUser().getId())) continue;
                notificationService.createAndPush(m, type, booking.getId(), title, content);
            }
        } catch (Exception e) {
            log.warn("Không thể gửi notification cho managers của booking {}: {}",
                    booking.getId(), e.getMessage());
        }
    }

    /**
     * Luồng 3: Hủy đơn
     */
    @Transactional
    public void cancelBooking(String bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn đặt lịch"));

        User currentUser = getCurrentUser();
        String oldStatus = booking.getStatus();

        if (!"PENDING".equalsIgnoreCase(oldStatus) && !"APPROVED".equalsIgnoreCase(oldStatus)) {
            throw new RuntimeException("Không thể hủy đơn đã bắt đầu mượn hoặc đã kết thúc");
        }

        // --- RULE: Không được hủy đơn APPROVED khi sát giờ mượn (< 30 phút) ---
        if ("APPROVED".equalsIgnoreCase(oldStatus)) {
            LocalDateTime bookingStartTime = LocalDateTime.of(booking.getBookingDate(),
                    booking.getSlot().getStartTime());
            long minutesToStart = Duration.between(LocalDateTime.now(), bookingStartTime).toMinutes();

            if (minutesToStart < 30) {
                throw new RuntimeException("Không thể hủy đơn đã được duyệt khi thời gian bắt đầu còn dưới 30 phút!");
            }
        }

        booking.setStatus("CANCELLED");
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelledReason(reason);
        bookingRepository.save(booking);

        saveHistory(booking, oldStatus, "CANCELLED", currentUser, "Hủy đơn: " + reason);

        // Chỉ noti Manager nếu đơn đã APPROVED (đã chiếm slot, manager cần biết để có
        // hành động). Đơn PENDING bị huỷ không cần làm phiền giáo vụ.
        if ("APPROVED".equalsIgnoreCase(oldStatus)) {
            String borrower = currentUser.getFullName() != null
                    ? currentUser.getFullName() : currentUser.getUsername();
            notifyManagersOfBooking(booking, "BOOKING_CANCELLED_BY_USER",
                    "Đơn đã duyệt vừa bị huỷ",
                    borrower + " đã huỷ đơn " + describeBooking(booking)
                            + ". Lý do: " + (reason != null ? reason : "(không có)"));
        }
    }

    /**
     * Luồng 4: Thủ kho Giao đồ (Check-in)
     */
    @Transactional
    public void checkIn(String token, String newSerialNumber) {
        Booking booking = bookingRepository.findByQrCodeToken(token)
                .orElseThrow(() -> new RuntimeException("Mã QR không hợp lệ hoặc đã hết hạn"));

        performCheckIn(booking, newSerialNumber);
    }

    @Transactional
    public void checkInBulkAuto(List<String> bookingIds) {
        User currentUser = getCurrentUser();
        List<Booking> bookings = bookingRepository.findAllById(bookingIds);

        for (Booking booking : bookings) {
            // Chỉ xử lý đơn APPROVED
            if (!"APPROVED".equalsIgnoreCase(booking.getStatus()))
                continue;

            // Kiểm tra bảo mật
            OrganizationUnit adminUnit = currentUser.getUnit();
            OrganizationUnit effectiveUnit = getEffectiveUnit(booking);
            if (adminUnit != null && effectiveUnit != null && !adminUnit.getId().equals(effectiveUnit.getId())) {
                continue; // Bỏ qua nếu không có quyền
            }

            // Tự động tìm máy thay thế nếu máy hiện tại không AVAILABLE
            String serialToUse = null;
            if (!"AVAILABLE".equalsIgnoreCase(booking.getResourceItem().getStatus())) {
                // Tìm máy khác cùng loại đang sẵn sàng
                List<ResourceItem> availableItems = resourceItemRepository.findAvailableItems(
                        booking.getResourceItem().getTemplate().getId(),
                        booking.getBookingDate(),
                        booking.getSlot().getStartTime(),
                        booking.getSlot().getEndTime());
                if (!availableItems.isEmpty()) {
                    serialToUse = availableItems.get(0).getSerialNumber();
                }
            }

            try {
                performCheckIn(booking, serialToUse);
            } catch (Exception e) {
                // Log and continue
                System.err.println("Lỗi bàn giao tự động cho đơn " + booking.getId() + ": " + e.getMessage());
            }
        }
    }

    @Transactional
    public void checkInBulkManual(BulkCheckInRequest request) {
        for (BulkCheckInRequest.ItemHandoverRequest itemReq : request.getItems()) {
            Booking booking = bookingRepository.findById(itemReq.getBookingId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn mượn: " + itemReq.getBookingId()));

            performCheckIn(booking, itemReq.getSerialNumber());
        }
    }

    private void performCheckIn(Booking booking, String newSerialNumber) {
        // --- LOGIC BIND (Gắn máy thực tế khi giáo vụ quét QR) ---
        if (newSerialNumber != null && !newSerialNumber.trim().isEmpty()) {
            ResourceItem newItem = resourceItemRepository.findBySerialNumber(newSerialNumber)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị có mã: " + newSerialNumber));

            ResourceItem oldItem = booking.getResourceItem();
            boolean sameAsPreAssigned = oldItem != null && newItem.getId().equals(oldItem.getId());

            // Kiểm tra loại thiết bị phải khớp với đơn mượn (tránh scan nhầm thiết bị khác)
            if (oldItem != null && oldItem.getTemplate() != null && newItem.getTemplate() != null
                    && !oldItem.getTemplate().getId().equals(newItem.getTemplate().getId())) {
                throw new RuntimeException("Thiết bị " + newSerialNumber
                        + " không cùng loại với đơn mượn (yêu cầu: " + oldItem.getTemplate().getName() + ")");
            }

            // Trừ trường hợp scan đúng máy đã pre-assign, máy phải đang AVAILABLE
            if (!sameAsPreAssigned && !"AVAILABLE".equalsIgnoreCase(newItem.getStatus())) {
                throw new RuntimeException("Thiết bị " + newSerialNumber + " hiện không sẵn sàng (Trạng thái: "
                        + newItem.getStatus() + ")");
            }

            if (!sameAsPreAssigned) {
                // oldItem là gợi ý nội bộ — status đang AVAILABLE (chưa bàn giao đơn này),
                // không cần đụng. Nếu oldItem đã IN_USE/BORROWED nghĩa là một booking khác
                // đã quét và đang dùng chính máy này — tuyệt đối không reset.
                booking.setResourceItem(newItem);
                newItem.setStatus("BORROWED");
                resourceItemRepository.save(newItem);
            }
        }

        // Sau bước trên, booking phải có resource_item — không cho phép handover mà không bind
        if (booking.getResourceItem() == null) {
            throw new RuntimeException("Đơn mượn chưa được gán thiết bị thực tế. "
                    + "Vui lòng quét QR thiết bị trước khi bàn giao.");
        }

        // Kiểm tra thời gian
        if (isExpired(booking)) {
            throw new RuntimeException("Đã quá giờ kết thúc ca mượn, không thể bàn giao thiết bị!");
        }

        LocalDate today = LocalDate.now();
        LocalDate maxAllowedDate = today.plusDays(3);
        if (booking.getBookingDate().isBefore(today) || booking.getBookingDate().isAfter(maxAllowedDate)) {
            throw new RuntimeException("Chỉ có thể bàn giao trong vòng 3 ngày kể từ hôm nay.");
        }

        User currentUser = getCurrentUser();
        OrganizationUnit adminUnit = currentUser.getUnit();
        OrganizationUnit effectiveUnit = getEffectiveUnit(booking);
        boolean isSuperAdmin = currentUser.getRoles().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getRoleCode()));

        if (!isSuperAdmin && effectiveUnit != null && adminUnit != null
                && !adminUnit.getId().equals(effectiveUnit.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền bàn giao thiết bị của đơn vị khác!");
        }

        String oldStatus = booking.getStatus();
        booking.setStatus("BORROWED");
        booking.setActualStartTime(LocalDateTime.now());

        // Cập nhật trạng thái máy
        ResourceItem currentItem = booking.getResourceItem();
        currentItem.setStatus("IN_USE");
        resourceItemRepository.save(currentItem);

        bookingRepository.save(booking);

        String historyMsg = "Thủ kho đã bàn giao thiết bị";
        if (newSerialNumber != null && !newSerialNumber.trim().isEmpty()) {
            historyMsg += " (Có đổi thiết bị sang Serial: " + newSerialNumber + ")";
        }
        saveHistory(booking, oldStatus, "BORROWED", currentUser, historyMsg);

        // Notify sinh viên — đã bàn giao
        notifyBookingActor(booking, "BOOKING_HANDOVER",
                "Đã nhận thiết bị thành công",
                "Thủ kho đã bàn giao " + describeBooking(booking)
                        + ". Vui lòng kiểm tra thiết bị và trả đúng giờ.");
    }

    /**
     * Luồng 5: Thủ kho Nhận lại đồ (Check-out)
     */
    @Transactional
    public void checkOut(String bookingId, EvidenceRequest evidenceRequest) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn mượn"));

        if (!"BORROWED".equalsIgnoreCase(booking.getStatus())) {
            throw new RuntimeException("Chỉ có thể trả đồ cho các đơn đang ở trạng thái ĐANG MƯỢN");
        }

        User currentUser = getCurrentUser();

        // --- BẮT ĐẦU CHỐT CHẶN BẢO MẬT CẤP ĐƠN VỊ (RLS) ---
        OrganizationUnit adminUnit = currentUser.getUnit();
        OrganizationUnit effectiveUnit = getEffectiveUnit(booking);

        boolean isSuperAdmin = currentUser.getRoles().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getRoleCode()));

        if (!isSuperAdmin) {
            // Chỉ block nếu thiết bị có gắn đơn vị và khác đơn vị của thủ kho
            if (effectiveUnit != null && adminUnit != null && !adminUnit.getId().equals(effectiveUnit.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Bạn không có quyền nhận lại thiết bị của đơn vị khác!");
            }
        }
        // --- KẾT THÚC CHỐT CHẶN ---

        String oldStatus = booking.getStatus();

        // 1. Cập nhật trạng thái booking
        booking.setStatus("RETURNED");
        booking.setActualEndTime(LocalDateTime.now());
        bookingRepository.save(booking);

        // 2. Lưu minh chứng (nếu có)
        boolean isDamage = false;
        if (evidenceRequest != null) {
            String evType = evidenceRequest.getEvidenceType();
            isDamage = "DAMAGE".equalsIgnoreCase(evType);
            BookingEvidence evidence = BookingEvidence.builder()
                    .booking(booking)
                    .resourceItem(isDamage ? booking.getResourceItem() : null)
                    .evidenceType(evType != null ? evType : "CHECK_OUT")
                    .imageUrl(evidenceRequest.getImageUrl())
                    .description(evidenceRequest.getDescription())
                    .createdBy(currentUser)
                    .build();
            evidenceRepository.save(evidence);
        }

        // 3. Giải phóng / cập nhật thiết bị
        ResourceItem item = booking.getResourceItem();
        if (isDamage) {
            item.setStatus("DAMAGED");
        } else {
            item.setStatus("AVAILABLE");
        }
        resourceItemRepository.save(item);

        saveHistory(booking, oldStatus, "RETURNED", currentUser,
                isDamage ? "Thủ kho nhận lại thiết bị (CÓ HƯ HỎNG)" : "Thủ kho đã nhận lại thiết bị");

        // 4. Auto-penalty cho hư hỏng
        if (isDamage) {
            String desc = String.format(
                    "Hư hỏng thiết bị %s khi trả (đơn ngày %s, ca %s). %s",
                    item != null && item.getTemplate() != null ? item.getTemplate().getName()
                            : "không xác định",
                    booking.getBookingDate(),
                    booking.getSlot() != null ? booking.getSlot().getSlotName() : "",
                    evidenceRequest != null && evidenceRequest.getDescription() != null
                            ? evidenceRequest.getDescription() : "");
            penaltyService.createSystemPenalty(
                    booking.getUser(), booking, "DAMAGE", damagePenaltyPoint, desc.trim());

            notifyBookingActor(booking, "BOOKING_DAMAGED",
                    "Ghi nhận hư hỏng thiết bị",
                    "Bạn bị ghi nhận hư hỏng thiết bị " + describeBooking(booking)
                            + " khi trả. Bạn đã bị trừ " + damagePenaltyPoint + " điểm tín nhiệm.");
        } else {
            notifyBookingActor(booking, "BOOKING_RETURNED",
                    "Đã trả thiết bị thành công",
                    "Cảm ơn bạn đã trả " + describeBooking(booking) + " đúng hạn.");
        }
    }

    /**
     * Lưu minh chứng bổ sung
     */
    @Transactional
    public void addEvidence(EvidenceRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn mượn"));

        BookingEvidence evidence = BookingEvidence.builder()
                .booking(booking)
                .evidenceType(request.getEvidenceType())
                .imageUrl(request.getImageUrl())
                .description(request.getDescription())
                .createdBy(getCurrentUser())
                .build();

        evidenceRepository.save(evidence);
    }

    private OrganizationUnit getEffectiveUnit(Booking booking) {
        if (booking.getManagedByUnit() != null) {
            return booking.getManagedByUnit();
        }
        if (booking.getResourceItem().getManagedByUnit() != null) {
            return booking.getResourceItem().getManagedByUnit();
        }
        if (booking.getResourceItem().getTemplate() != null) {
            return booking.getResourceItem().getTemplate().getUnit();
        }
        return null;
    }

    private void saveHistory(Booking booking, String oldStatus, String newStatus, User user, String reason) {
        BookingHistory history = BookingHistory.builder()
                .booking(booking)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(user)
                .reason(reason)
                .build();
        historyRepository.save(history);
    }

    private User getCurrentUser() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication()
                .getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng hiện tại"));
    }

    @Transactional
    public void autoCancelExpiredBookings() {
        // Quét cả đơn CHỜ DUYỆT và ĐÃ DUYỆT (nhưng chưa đến lấy đồ)
        List<String> statuses = List.of("PENDING", "APPROVED");
        List<Booking> expiredCandidates = bookingRepository.findAllByStatusIn(statuses);

        int count = 0;
        for (Booking booking : expiredCandidates) {
            if (isExpired(booking)) {
                String oldStatus = booking.getStatus();
                booking.setStatus("CANCELLED");
                booking.setCancelledReason(
                        "Tự động hủy do hết giờ ca mượn (Sinh viên không đến nhận đồ hoặc Manager không duyệt kịp)");
                booking.setCancelledAt(LocalDateTime.now());

                // Giải phóng thiết bị nếu đơn đã ở trạng thái APPROVED.
                // CHỈ đụng vào item đang RESERVED / AVAILABLE; tuyệt đối không reset
                // item đang IN_USE (đã handover cho user khác), MAINTENANCE, DAMAGED, LOST.
                if ("APPROVED".equalsIgnoreCase(oldStatus) && booking.getResourceItem() != null) {
                    ResourceItem item = booking.getResourceItem();
                    String currentItemStatus = item.getStatus();
                    if ("RESERVED".equalsIgnoreCase(currentItemStatus)
                            || "AVAILABLE".equalsIgnoreCase(currentItemStatus)
                            || currentItemStatus == null) {
                        item.setStatus("AVAILABLE");
                        resourceItemRepository.save(item);
                    } else {
                        log.warn("autoCancelExpiredBookings: bỏ qua item {} (status={}) cho booking {} — không reset để tránh ghi đè state hợp lệ.",
                                item.getId(), currentItemStatus, booking.getId());
                    }
                }

                bookingRepository.save(booking);
                saveHistory(booking, oldStatus, "CANCELLED", null, "Hệ thống tự động dọn dẹp đơn quá hạn");

                // Đơn APPROVED bị quá giờ ca = sinh viên đã được duyệt nhưng KHÔNG đến nhận đồ.
                // → ghi NO_SHOW (trừ điểm nhẹ). Còn PENDING không phạt vì có thể do quản lý chưa kịp duyệt.
                if ("APPROVED".equalsIgnoreCase(oldStatus) && booking.getUser() != null) {
                    String desc = String.format(
                            "Sinh viên không đến nhận thiết bị %s (ca %s ngày %s) — đơn tự huỷ.",
                            booking.getResourceItem() != null
                                    && booking.getResourceItem().getTemplate() != null
                                            ? booking.getResourceItem().getTemplate().getName()
                                            : "không xác định",
                            booking.getSlot() != null ? booking.getSlot().getSlotName() : "?",
                            booking.getBookingDate());
                    try {
                        penaltyService.createSystemPenalty(
                                booking.getUser(), booking, "NO_SHOW", noShowPenaltyPoint, desc);
                    } catch (Exception ex) {
                        log.warn("Không tạo được NO_SHOW penalty cho booking {}: {}",
                                booking.getId(), ex.getMessage());
                    }
                    notifyBookingActor(booking, "BOOKING_AUTO_CANCELLED",
                            "Đơn mượn đã bị huỷ — bạn bị phạt NO_SHOW",
                            "Đơn " + describeBooking(booking)
                                    + " đã được duyệt nhưng bạn không đến nhận đồ trước khi ca kết thúc. "
                                    + "Bị trừ " + noShowPenaltyPoint + " điểm tín nhiệm.");
                } else {
                    notifyBookingActor(booking, "BOOKING_AUTO_CANCELLED",
                            "Đơn mượn đã bị huỷ tự động",
                            "Đơn " + describeBooking(booking)
                                    + " bị huỷ do quá giờ ca mượn. Vui lòng đặt lại nếu vẫn cần mượn.");
                }
                count++;
            }
        }
        if (count > 0) {
            log.info("Đã tự động hủy {} đơn mượn quá hạn.", count);
        }
    }

    private boolean isExpired(Booking booking) {
        if (booking.getSlot() == null || booking.getBookingDate() == null)
            return false;

        LocalDateTime now = LocalDateTime.now();
        // Thời điểm kết thúc ca mượn
        LocalDateTime slotEndTime = LocalDateTime.of(booking.getBookingDate(), booking.getSlot().getEndTime());

        // Nếu hiện tại đã quá giờ kết thúc ca mượn
        return now.isAfter(slotEndTime);
    }

    /**
     * Một booking được coi là "đã bind máy thực tế" khi nó đã đi qua khâu bàn giao
     * (giáo vụ scan QR thiết bị). Trước đó, resource_item gắn vào booking chỉ là
     * một gợi ý nội bộ phục vụ tính tồn kho — không nên expose serial cho người dùng.
     */
    private boolean isItemBound(String status) {
        return "BORROWED".equalsIgnoreCase(status)
                || "RETURNED".equalsIgnoreCase(status)
                || "OVERDUE".equalsIgnoreCase(status);
    }

    /**
     * Quét tất cả booking đang BORROWED nhưng đã quá giờ kết thúc ca + grace minutes
     * → tự động sinh Penalty LATE_RETURN. Idempotent: PenaltyService kiểm tra
     * existsBy... trước khi tạo nên gọi nhiều lần không sinh trùng.
     */
    @Transactional
    public void autoPenalizeOverdueReturns() {
        // Lấy trước danh sách id — bên trong createSystemPenalty có UPDATE @Modifying(clearAutomatically=true)
        // sẽ detach toàn bộ entity đã cache, gây LazyInitializationException ở iteration kế tiếp khi
        // truy cập lazy association (resourceItem, slot, user...). Reload từng booking trong vòng lặp.
        List<String> bookingIds = bookingRepository.findAllByStatusIn(List.of("BORROWED"))
                .stream()
                .map(Booking::getId)
                .toList();
        LocalDateTime now = LocalDateTime.now();
        int count = 0;

        for (String bookingId : bookingIds) {
            Booking booking = bookingRepository.findById(bookingId).orElse(null);
            if (booking == null
                    || booking.getSlot() == null || booking.getBookingDate() == null
                    || booking.getUser() == null) {
                continue;
            }

            LocalDateTime slotEnd = LocalDateTime.of(
                    booking.getBookingDate(), booking.getSlot().getEndTime());
            LocalDateTime overdueAt = slotEnd.plusMinutes(lateReturnGraceMinutes);

            if (now.isAfter(overdueAt)) {
                long minutesLate = Duration.between(slotEnd, now).toMinutes();
                String desc = String.format(
                        "Trễ trả thiết bị %s (%d phút sau giờ kết thúc ca %s ngày %s)",
                        booking.getResourceItem() != null
                                && booking.getResourceItem().getTemplate() != null
                                        ? booking.getResourceItem().getTemplate().getName()
                                        : "không xác định",
                        minutesLate,
                        booking.getSlot().getSlotName(),
                        booking.getBookingDate());

                // Snapshot status trước khi createSystemPenalty clear context.
                String currentStatus = booking.getStatus();

                Penalty p = penaltyService.createSystemPenalty(
                        booking.getUser(), booking, "OVERDUE", latePenaltyPoint, desc);
                if (p != null) {
                    saveHistory(booking, currentStatus, currentStatus, null,
                            "Hệ thống tự động ghi nhận phạt LATE_RETURN (-" + latePenaltyPoint + "đ)");
                    count++;
                }
            }
        }
        if (count > 0) {
            log.info("Đã tự động sinh {} penalty LATE_RETURN cho đơn quá hạn trả.", count);
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBatchByQrToken(String token) {
        // 1. Thử tìm theo mã QR của từng thiết bị (Item QR)
        Optional<Booking> bookingOpt = bookingRepository.findByQrCodeToken(token);
        if (bookingOpt.isPresent()) {
            Booking booking = bookingOpt.get();
            String batchToken = booking.getBatchToken();
            if (batchToken == null) {
                return List.of(mapToResponse(booking));
            }
            return bookingRepository.findAllByBatchToken(batchToken).stream().map(this::mapToResponse).toList();
        }

        // 2. Thử tìm trực tiếp theo mã lô (Batch QR)
        List<Booking> batch = bookingRepository.findAllByBatchToken(token);
        if (!batch.isEmpty()) {
            return batch.stream().map(this::mapToResponse).toList();
        }

        throw new RuntimeException("Mã QR không hợp lệ hoặc đã hết hạn");
    }

    @Transactional
    public void returnBulk(com.example.i_resource_hub.dto.request.BulkReturnRequest request) {
        if (request == null || request.getBookingIds() == null || request.getBookingIds().isEmpty())
            return;

        List<String> ids = request.getBookingIds();
        User currentUser = getCurrentUser();

        // Map damage info for quick lookup
        Map<String, com.example.i_resource_hub.dto.request.BulkReturnRequest.ItemDamageRequest> damageMap = new HashMap<>();
        if (request.getDamages() != null) {
            for (com.example.i_resource_hub.dto.request.BulkReturnRequest.ItemDamageRequest d : request.getDamages()) {
                damageMap.put(d.getBookingId(), d);
            }
        }

        // Reload từng booking trong vòng lặp: createSystemPenalty bên dưới có
        // @Modifying(clearAutomatically=true) clear persistence context → các proxy lazy
        // (resourceItem, slot, user) bị detach, gây LazyInitializationException ở iteration kế tiếp.
        for (String bookingId : ids) {
            Booking booking = bookingRepository.findById(bookingId).orElse(null);
            if (booking == null || !"BORROWED".equalsIgnoreCase(booking.getStatus()))
                continue;
            ResourceItem item = booking.getResourceItem();
            com.example.i_resource_hub.dto.request.BulkReturnRequest.ItemDamageRequest damageInfo = damageMap
                    .get(bookingId);

            // 1. Cập nhật trạng thái item
            if (item != null) {
                if (damageInfo != null) {
                    item.setStatus("DAMAGED");
                    // Lưu minh chứng hư hỏng (Tái sử dụng BookingEvidence)
                    BookingEvidence evidence = BookingEvidence.builder()
                            .booking(booking)
                            .resourceItem(item) // Liên kết trực tiếp với máy hỏng
                            .evidenceType("DAMAGE")
                            .imageUrl(damageInfo.getImageUrl())
                            .description(damageInfo.getDescription())
                            .createdBy(currentUser)
                            .build();
                    evidenceRepository.save(evidence);
                    log.info("Ghi nhận thiết bị hỏng: {}", item.getSerialNumber());
                } else {
                    item.setStatus("AVAILABLE");
                }
                resourceItemRepository.save(item);
            }

            // 2. Cập nhật trạng thái Booking và kiểm tra lô
            booking.setStatus("RETURNED");
            booking.setActualEndTime(LocalDateTime.now());
            bookingRepository.save(booking);

            saveHistory(booking, "BORROWED", "RETURNED", currentUser,
                    damageInfo != null ? "Thủ kho nhận lại thiết bị (CÓ HƯ HỎNG)" : "Thủ kho nhận lại thiết bị");

            // 3. Auto-penalty cho hư hỏng
            if (damageInfo != null && booking.getUser() != null) {
                String desc = String.format(
                        "Hư hỏng thiết bị %s khi trả (đơn ngày %s, ca %s). %s",
                        item != null && item.getTemplate() != null ? item.getTemplate().getName()
                                : "không xác định",
                        booking.getBookingDate(),
                        booking.getSlot() != null ? booking.getSlot().getSlotName() : "",
                        damageInfo.getDescription() != null ? damageInfo.getDescription() : "");
                penaltyService.createSystemPenalty(
                        booking.getUser(), booking, "DAMAGE", damagePenaltyPoint, desc.trim());
            }
        }
    }
}
