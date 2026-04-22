package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.ActionRequest;
import com.example.i_resource_hub.dto.request.BookingRequest;
import com.example.i_resource_hub.dto.request.BulkBookingRequest;
import com.example.i_resource_hub.dto.request.EvidenceRequest;
import com.example.i_resource_hub.dto.response.BookingResponse;
import com.example.i_resource_hub.entity.*;
import com.example.i_resource_hub.repository.*;
import com.example.i_resource_hub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingHistoryRepository historyRepository;
    private final BookingEvidenceRepository evidenceRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

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

        // Kiểm tra đơn vị (Khoa) - Phải thuộc một khoa mới được mượn đồ của khoa đó
        if (currentUser.getUnit() == null) {
            throw new RuntimeException("Tài khoản của bạn chưa được gán vào Đơn vị/Khoa nào để thực hiện mượn đồ!");
        }
        String unitId = currentUser.getUnit().getId();

        // Kiểm tra chặn quá khứ (Time Validation)
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
        
        // 1. Tìm tất cả thiết bị khả dụng
        List<ResourceItem> availableItems = resourceItemRepository.findAvailableItems(
                request.getResourceTemplateId(), 
                unitId,
                request.getBookingDate(), 
                request.getSlotId()
        );

        int requestedQty = (request.getQuantity() != null && request.getQuantity() > 0) ? request.getQuantity() : 1;

        if (availableItems.size() < requestedQty) {
            throw new RuntimeException("Chỉ còn " + availableItems.size() + " thiết bị khả dụng. Không đủ số lượng " + requestedQty + " bạn yêu cầu!");
        }

        // 2. Tạo danh sách Booking
        Booking lastSavedBooking = null;
        for (int i = 0; i < requestedQty; i++) {
            ResourceItem item = availableItems.get(i);
            
            Booking booking = Booking.builder()
                    .user(currentUser)
                    .resourceItem(item)
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
            saveHistory(lastSavedBooking, null, "PENDING", currentUser, "Tạo đơn đăng ký mới (Số lượng: " + requestedQty + ")");
        }

        return mapToResponse(lastSavedBooking);
    }

    /**
     * Luồng mượn nhiều loại đồ khác nhau cùng lúc (Batch/Bulk Booking)
     */
    @Transactional
    public void createBulkBookings(BulkBookingRequest request) {
        User currentUser = getCurrentUser();
        if (currentUser.getUnit() == null) {
            throw new RuntimeException("Tài khoản chưa được gán đơn vị!");
        }
        String unitId = currentUser.getUnit().getId();

        // Duyệt qua từng loại đồ trong danh sách
        for (BulkBookingRequest.BookingItemRequest itemReq : request.getItems()) {
            
            TimeSlot slot = timeSlotRepository.findById(itemReq.getSlotId())
                    .orElseThrow(() -> new RuntimeException("Khung giờ không hợp lệ cho món đồ có ID: " + itemReq.getResourceTemplateId()));

            // Validate thời gian cho từng món
            if (itemReq.getBookingDate().isBefore(LocalDate.now())) {
                throw new RuntimeException("Ngày mượn không hợp lệ (trong quá khứ) cho một số món đồ!");
            }

            List<ResourceItem> availableItems = resourceItemRepository.findAvailableItems(
                    itemReq.getResourceTemplateId(), 
                    unitId,
                    itemReq.getBookingDate(), 
                    itemReq.getSlotId()
            );

            int requestedQty = (itemReq.getQuantity() != null && itemReq.getQuantity() > 0) ? itemReq.getQuantity() : 1;

            if (availableItems.size() < requestedQty) {
                throw new RuntimeException("Thiết bị mượn không đủ số lượng cho món: " + itemReq.getResourceTemplateId());
            }

            for (int i = 0; i < requestedQty; i++) {
                Booking booking = Booking.builder()
                        .user(currentUser)
                        .resourceItem(availableItems.get(i))
                        .bookingDate(itemReq.getBookingDate())
                        .slot(slot)
                        .purpose(request.getPurpose())
                        .status("PENDING")
                        .qrCodeToken(UUID.randomUUID().toString())
                        .build();
                
                Booking saved = bookingRepository.save(booking);
                saveHistory(saved, null, "PENDING", currentUser, "Tạo đơn mượn trong danh sách (Bulk)");
            }
        }
    }

    /**
     * Map Entity sang DTO để Frontend dễ dùng
     */
    private BookingResponse mapToResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUser() != null ? booking.getUser().getId() : null)
                .borrowerName(booking.getUser() != null ? booking.getUser().getFullName() : "N/A")
                .borrowerId(booking.getUser() != null ? booking.getUser().getStudentCode() : "N/A")
                .resourceItemId(booking.getResourceItem() != null ? booking.getResourceItem().getId() : null)
                .deviceName(booking.getResourceItem() != null && booking.getResourceItem().getTemplate() != null 
                        ? booking.getResourceItem().getTemplate().getName() : "Thiết bị không tên")
                .serialNumber(booking.getResourceItem() != null ? booking.getResourceItem().getSerialNumber() : "")
                .bookingDate(booking.getBookingDate())
                .slotId(booking.getSlot() != null ? booking.getSlot().getId() : null)
                .slotName(booking.getSlot() != null ? booking.getSlot().getSlotName() : "")
                .startTime(booking.getSlot() != null ? booking.getSlot().getStartTime().toString() : "")
                .endTime(booking.getSlot() != null ? booking.getSlot().getEndTime().toString() : "")
                .status(booking.getStatus())
                .qrCodeToken(booking.getQrCodeToken())
                .purpose(booking.getPurpose())
                .build();
    }

    /**
     * Lấy danh sách đơn mượn theo Đơn vị của User hiện tại (cho bảng Kanban)
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getKanbanBookings() {
        User currentUser = getCurrentUser();
        
        // Nếu không thuộc đơn vị nào, coi như không thấy gì (hoặc thấy hết tùy logic hệ thống)
        if (currentUser.getUnit() == null) {
            return List.of();
        }

        return bookingRepository.findByResourceItem_ManagedByUnit_Id(currentUser.getUnit().getId())
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
        
        User currentUser = getCurrentUser();

        // --- BẮT ĐẦU CHỐT CHẶN BẢO MẬT CẤP ĐƠN VỊ (RLS) ---
        OrganizationUnit adminUnit = currentUser.getUnit();
        OrganizationUnit itemOwnerUnit = booking.getResourceItem().getManagedByUnit();

        if (adminUnit == null || itemOwnerUnit == null || !adminUnit.getId().equals(itemOwnerUnit.getId())) {
             throw new org.springframework.security.access.AccessDeniedException(
                "Bạn không có quyền xử lý đơn mượn thiết bị của đơn vị khác!"
             );
        }
        // --- KẾT THÚC CHỐT CHẶN ---

        String oldStatus = booking.getStatus();

        if (!"PENDING".equalsIgnoreCase(oldStatus)) {
            throw new RuntimeException("Chỉ có thể phê duyệt hoặc từ chối các đơn đang ở trạng thái CHỜ DUYỆT");
        }

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            booking.setStatus("APPROVED");
            booking.setApprovedAt(LocalDateTime.now());
            booking.setApprovedBy(currentUser);
            saveHistory(booking, oldStatus, "APPROVED", currentUser, "Đã phê duyệt đơn");
        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            booking.setStatus("REJECTED");
            booking.setRejectedReason(request.getReason());
            saveHistory(booking, oldStatus, "REJECTED", currentUser, "Từ chối đơn: " + request.getReason());
        } else {
            throw new RuntimeException("Hành động không hợp lệ");
        }

        bookingRepository.save(booking);
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
            LocalDateTime bookingStartTime = LocalDateTime.of(booking.getBookingDate(), booking.getSlot().getStartTime());
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
    }

    /**
     * Luồng 4: Thủ kho Giao đồ (Check-in)
     */
    @Transactional
    public void checkIn(String qrCodeToken) {
        Booking booking = bookingRepository.findByQrCodeToken(qrCodeToken)
                .orElseThrow(() -> new RuntimeException("Mã QR không hợp lệ hoặc đã hết hạn"));
        
        if (!"APPROVED".equalsIgnoreCase(booking.getStatus())) {
            throw new RuntimeException("Đơn này chưa được duyệt hoặc đã xử lý rồi");
        }

        // Cho phép bàn giao trước ngày mượn tối đa 3 ngày
        LocalDate today = LocalDate.now();
        LocalDate maxAllowedDate = today.plusDays(3);
        if (booking.getBookingDate().isBefore(today) || booking.getBookingDate().isAfter(maxAllowedDate)) {
            throw new RuntimeException(
                "Chỉ có thể bàn giao trong vòng 3 ngày kể từ hôm nay (từ " + today + " đến " + maxAllowedDate + ")"
            );
        }

        User currentUser = getCurrentUser();

        // --- BẮT ĐẦU CHỐT CHẶN BẢO MẬT CẤP ĐƠN VỊ (RLS) ---
        OrganizationUnit adminUnit = currentUser.getUnit();
        OrganizationUnit itemOwnerUnit = booking.getResourceItem().getManagedByUnit();

        boolean isSuperAdmin = currentUser.getRoles().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getRoleCode()));

        if (!isSuperAdmin) {
            // Chỉ block nếu thiết bị có gắn đơn vị và khác đơn vị của thủ kho
            if (itemOwnerUnit != null && adminUnit != null && !adminUnit.getId().equals(itemOwnerUnit.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Bạn không có quyền bàn giao thiết bị của đơn vị khác!"
                );
            }
        }
        // --- KẾT THÚC CHỐT CHẶN ---

        String oldStatus = booking.getStatus();

        booking.setStatus("BORROWED");
        booking.setActualStartTime(LocalDateTime.now());
        bookingRepository.save(booking);

        saveHistory(booking, oldStatus, "BORROWED", currentUser, "Thủ kho đã bàn giao thiết bị");
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
        OrganizationUnit itemOwnerUnit = booking.getResourceItem().getManagedByUnit();

        boolean isSuperAdmin = currentUser.getRoles().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getRoleCode()));

        if (!isSuperAdmin) {
            // Chỉ block nếu thiết bị có gắn đơn vị và khác đơn vị của thủ kho
            if (itemOwnerUnit != null && adminUnit != null && !adminUnit.getId().equals(itemOwnerUnit.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Bạn không có quyền nhận lại thiết bị của đơn vị khác!"
                );
            }
        }
        // --- KẾT THÚC CHỐT CHẶN ---

        String oldStatus = booking.getStatus();

        // 1. Cập nhật trạng thái booking
        booking.setStatus("RETURNED");
        booking.setActualEndTime(LocalDateTime.now());
        bookingRepository.save(booking);

        // 2. Lưu minh chứng (bắt buộc ảnh lúc trả)
        BookingEvidence evidence = BookingEvidence.builder()
                .booking(booking)
                .evidenceType("CHECK_OUT")
                .imageUrl(evidenceRequest.getImageUrl())
                .description(evidenceRequest.getDescription())
                .createdBy(currentUser)
                .build();
        evidenceRepository.save(evidence);

        // 3. Giải phóng thiết bị (nếu cần xử lý thêm trạng thái thiết bị ở đây)
        ResourceItem item = booking.getResourceItem();
        item.setStatus("AVAILABLE");
        resourceItemRepository.save(item);

        saveHistory(booking, oldStatus, "RETURNED", currentUser, "Thủ kho đã nhận lại thiết bị");
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
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng hiện tại"));
    }
}
