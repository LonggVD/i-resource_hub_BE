package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.ActionRequest;
import com.example.i_resource_hub.dto.request.BookingRequest;
import com.example.i_resource_hub.dto.request.EvidenceRequest;
import com.example.i_resource_hub.dto.response.BookingResponse;
import com.example.i_resource_hub.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Management", description = "Các API quản lý việc đặt lịch và mượn trả thiết bị")
public class BookingController {

    private final BookingService bookingService;

    /**
     * Lấy toàn bộ danh sách đơn mượn (cho bảng Kanban)
     */
    @Operation(summary = "Lấy danh sách tất cả đơn mượn", description = "Lấy toàn bộ đơn mượn để hiển thị lên bảng Kanban")
    @GetMapping
    public ResponseEntity<List<BookingResponse>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    /**
     * Lấy danh sách đơn mượn cho Kanban (Đã lọc theo Đơn vị của người đăng nhập)
     */
    @Operation(summary = "Lấy danh sách đơn mượn cho Kanban (Theo Đơn vị)", description = "Chỉ lấy các đơn mượn thiết bị thuộc quyền quản lý của đơn vị người đăng nhập (Quyền: BOOKING_APPROVE)")
    @GetMapping("/kanban")
    @PreAuthorize("hasAuthority('BOOKING_APPROVE')")
    public ResponseEntity<List<BookingResponse>> getKanbanBookings() {
        System.out.println(bookingService.getKanbanBookings());
        return ResponseEntity.ok(bookingService.getKanbanBookings());
    }

    /**
     * Lấy danh sách đơn mượn của chính người đăng nhập (Dành cho Sinh viên)
     */
    @Operation(summary = "Lấy danh sách đơn mượn của tôi", description = "Chỉ lấy các đơn mượn do chính người đăng nhập tạo")
    @GetMapping("/my")
    public ResponseEntity<List<BookingResponse>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    /**
     * Sinh viên đặt lịch
     */
    @Operation(summary = "Đặt lịch thiết bị", description = "Cho phép sinh viên tạo đơn mượn thiết bị mới (Quyền: BOOKING_CREATE)")
    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.ok(bookingService.createBooking(request));
    }

    /**
     * Mượn nhiều loại đồ cùng lúc (Bulk)
     */
    @Operation(summary = "Mượn nhiều loại thiết bị cùng lúc", description = "Cho phép tạo nhiều đơn mượn cho các loại thiết bị khác nhau trong cùng một lần xác nhận")
    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    public ResponseEntity<String> createBulkBookings(@Valid @RequestBody com.example.i_resource_hub.dto.request.BulkBookingRequest request) {
        bookingService.createBulkBookings(request);
        return ResponseEntity.ok("Gửi danh sách yêu cầu mượn thành công");
    }

    /**
     * Giáo vụ duyệt / từ chối
     */
    @Operation(summary = "Phê duyệt hoặc Từ chối đơn mượn", description = "Dành cho giáo vụ xử lý các đơn mượn đang PENDING (Quyền: BOOKING_APPROVE)")
    @PutMapping("/{id}/process")
    @PreAuthorize("hasAuthority('BOOKING_APPROVE')")
    public ResponseEntity<String> processAction(
            @Parameter(description = "ID của đơn mượn") @PathVariable String id,
            @Valid @RequestBody ActionRequest request) {
        bookingService.processAction(id, request);
        return ResponseEntity.ok("Xử lý đơn thành công");
    }

    /**
     * Người dùng hủy đơn
     */
    @Operation(summary = "Hủy đơn mượn", description = "Cho phép người đặt hoặc quản trị viên hủy đơn mượn")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<String> cancelBooking(
            @Parameter(description = "ID của đơn mượn") @PathVariable String id,
            @Parameter(description = "Lý do hủy") @RequestParam String reason) {
        bookingService.cancelBooking(id, reason);
        return ResponseEntity.ok("Hủy đơn thành công");
    }

    /**
     * Thủ kho bàn giao đồ (Check-in qua QR)
     */
    @Operation(summary = "Bàn giao thiết bị (Check-in)", description = "Thủ kho quét mã QR của sinh viên để xác nhận giao đồ (Quyền: INVENTORY_CHECKIN hoặc BOOKING_APPROVE)")
    @PostMapping("/check-in")
    @PreAuthorize("hasAnyAuthority('INVENTORY_CHECKIN', 'BOOKING_APPROVE')")
    public ResponseEntity<String> checkIn(
            @Parameter(description = "Token UUID lấy từ mã QR") @RequestParam String token) {
        bookingService.checkIn(token);
        return ResponseEntity.ok("Bàn giao thiết bị thành công");
    }

    /**
     * Thủ kho nhận lại đồ (Check-out)
     */
    @Operation(summary = "Nhận lại thiết bị (Check-out)", description = "Xác nhận sinh viên đã trả đồ và chụp ảnh minh chứng (Quyền: INVENTORY_CHECKOUT hoặc BOOKING_APPROVE)")
    @PostMapping("/check-out")
    @PreAuthorize("hasAnyAuthority('INVENTORY_CHECKOUT', 'BOOKING_APPROVE')")
    public ResponseEntity<String> checkOut(@Valid @RequestBody EvidenceRequest request) {
        bookingService.checkOut(request.getBookingId(), request);
        return ResponseEntity.ok("Nhận lại thiết bị thành công");
    }

    /**
     * Thêm minh chứng cho đơn mượn
     */
    @Operation(summary = "Thêm minh chứng ảnh", description = "Lưu thêm ảnh minh chứng tình trạng thiết bị")
    @PostMapping("/evidence")
    public ResponseEntity<String> addEvidence(@Valid @RequestBody EvidenceRequest request) {
        bookingService.addEvidence(request);
        return ResponseEntity.ok("Thêm minh chứng thành công");
    }
}
