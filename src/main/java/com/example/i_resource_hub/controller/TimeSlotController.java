package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.TimeSlotRequest;
import com.example.i_resource_hub.dto.response.TimeSlotResponse;
import com.example.i_resource_hub.service.TimeSlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ┌──────────────────────────────────────────────────────────┐
 * │              TimeSlot – Cách hoạt động                   │
 * ├──────────────────────────────────────────────────────────┤
 * │ TimeSlot đại diện cho một "khung giờ mượn" trong ngày.   │
 * │                                                          │
 * │ Ví dụ thực tế:                                           │
 * │   Ca 1: 07:00 – 09:15 (Thứ Hai → Thứ Sáu)              │
 * │   Ca 2: 09:30 – 11:45 (Thứ Hai → Thứ Sáu)              │
 * │   Ca 3: 13:00 – 15:15 (tất cả các ngày)                 │
 * │                                                          │
 * │ Luồng sử dụng:                                           │
 * │  1. ADMIN tạo các TimeSlot (POST /api/time-slots)        │
 * │  2. Sinh viên gọi GET /api/time-slots để lấy danh sách   │
 * │     và chọn slot khi tạo Booking                         │
 * │  3. Booking lưu FK slot_id → TimeSlot (bảng bookings)    │
 * │  4. Kanban / Manager xem slotName, startTime, endTime    │
 * │     trên card của đơn mượn                               │
 * └──────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/time-slots")
@RequiredArgsConstructor
@Tag(name = "Time Slot Management", description = "Quản lý các khung giờ mượn thiết bị (ca học, ca làm việc)")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    // ═══════════════════════════════════════════════════════
    //  READ endpoints  (mọi người đã đăng nhập đều xem được)
    // ═══════════════════════════════════════════════════════

    @Operation(
            summary = "Lấy toàn bộ danh sách khung giờ",
            description = "Trả về tất cả TimeSlot hiện có. " +
                          "Sinh viên dùng API này để chọn ca khi tạo đơn mượn."
    )
    @GetMapping
    public ResponseEntity<List<TimeSlotResponse>> getAllTimeSlots() {
        return ResponseEntity.ok(timeSlotService.getAllTimeSlots());
    }

    @Operation(
            summary = "Lấy khung giờ theo ngày trong tuần",
            description = "Lọc TimeSlot theo dayOfWeek (1=Thứ Hai … 7=Chủ Nhật). " +
                          "Hữu ích khi frontend muốn hiển thị ca theo ngày đã chọn."
    )
    @GetMapping("/by-day")
    public ResponseEntity<List<TimeSlotResponse>> getSlotsByDay(
            @Parameter(description = "Ngày trong tuần (1=Thứ Hai ... 7=Chủ Nhật)", example = "2")
            @RequestParam Integer dayOfWeek) {
        return ResponseEntity.ok(timeSlotService.getSlotsByDay(dayOfWeek));
    }

    @Operation(
            summary = "Lấy các khung giờ áp dụng mọi ngày",
            description = "Chỉ lấy những TimeSlot không gắn với ngày cụ thể (dayOfWeek = null)."
    )
    @GetMapping("/common")
    public ResponseEntity<List<TimeSlotResponse>> getCommonSlots() {
        return ResponseEntity.ok(timeSlotService.getCommonSlots());
    }

    @Operation(
            summary = "Lấy chi tiết một khung giờ",
            description = "Lấy thông tin đầy đủ của một TimeSlot theo ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TimeSlotResponse> getById(
            @Parameter(description = "ID của TimeSlot") @PathVariable String id) {
        return ResponseEntity.ok(timeSlotService.getById(id));
    }

    // ═══════════════════════════════════════════════════════
    //  WRITE endpoints  (chỉ ADMIN / RESOURCE_MANAGE)
    // ═══════════════════════════════════════════════════════

    @Operation(
            summary = "Tạo mới khung giờ",
            description = "Chỉ ADMIN mới có thể tạo TimeSlot mới. " +
                          "Nếu dayOfWeek = null, ca sẽ áp dụng cho tất cả các ngày trong tuần."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc tên ca đã tồn tại")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<TimeSlotResponse> create(@Valid @RequestBody TimeSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(timeSlotService.create(request));
    }

    @Operation(
            summary = "Cập nhật khung giờ",
            description = "Chỉnh sửa tên, giờ bắt đầu/kết thúc hoặc ngày trong tuần của TimeSlot. " +
                          "Yêu cầu quyền RESOURCE_MANAGE."
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<TimeSlotResponse> update(
            @Parameter(description = "ID của TimeSlot cần sửa") @PathVariable String id,
            @Valid @RequestBody TimeSlotRequest request) {
        return ResponseEntity.ok(timeSlotService.update(id, request));
    }

    @Operation(
            summary = "Xóa khung giờ",
            description = "Đánh dấu TimeSlot là đã xóa (soft-delete). " +
                          "Dữ liệu Booking cũ vẫn còn nguyên. Yêu cầu quyền RESOURCE_MANAGE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy TimeSlot")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID của TimeSlot cần xóa") @PathVariable String id) {
        timeSlotService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
