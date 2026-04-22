package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.TimeSlotRequest;
import com.example.i_resource_hub.dto.response.TimeSlotResponse;
import com.example.i_resource_hub.entity.TimeSlot;
import com.example.i_resource_hub.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;

    // ─── Bảng ánh xạ dayOfWeek → tên tiếng Việt ──────────────────────────────
    private static final Map<Integer, String> DAY_LABELS = Map.of(
            1, "Thứ Hai",
            2, "Thứ Ba",
            3, "Thứ Tư",
            4, "Thứ Năm",
            5, "Thứ Sáu",
            6, "Thứ Bảy",
            7, "Chủ Nhật"
    );

    // ─── READ: Lấy tất cả khung giờ ──────────────────────────────────────────

    /**
     * Trả về toàn bộ danh sách TimeSlot.
     * Sinh viên dùng API này để chọn ca khi tạo Booking.
     */
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getAllTimeSlots() {
        return timeSlotRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lọc danh sách TimeSlot theo ngày trong tuần (1=Thứ 2 … 7=CN).
     * Rất hữu ích khi giao diện chọn ca theo từng ngày.
     */
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getSlotsByDay(Integer dayOfWeek) {
        return timeSlotRepository.findByDayOfWeek(dayOfWeek).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy các ca áp dụng mọi ngày (dayOfWeek = null).
     */
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getCommonSlots() {
        return timeSlotRepository.findByDayOfWeekIsNull().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy thông tin một TimeSlot cụ thể theo ID.
     */
    @Transactional(readOnly = true)
    public TimeSlotResponse getById(String id) {
        TimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khung giờ với ID: " + id));
        return mapToResponse(slot);
    }

    // ─── WRITE: Tạo / Sửa / Xóa khung giờ (chỉ ADMIN) ──────────────────────

    /**
     * Tạo mới một TimeSlot.
     */
    @Transactional
    public TimeSlotResponse create(TimeSlotRequest request) {
        if (timeSlotRepository.existsBySlotName(request.getSlotName())) {
            throw new RuntimeException("Tên khung giờ \"" + request.getSlotName() + "\" đã tồn tại!");
        }
        TimeSlot slot = TimeSlot.builder()
                .slotName(request.getSlotName())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .dayOfWeek(request.getDayOfWeek())
                .build();
        return mapToResponse(timeSlotRepository.save(slot));
    }

    /**
     * Cập nhật thông tin TimeSlot.
     */
    @Transactional
    public TimeSlotResponse update(String id, TimeSlotRequest request) {
        TimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khung giờ với ID: " + id));

        // Kiểm tra tên mới có trùng với ca khác không
        if (!slot.getSlotName().equals(request.getSlotName())
                && timeSlotRepository.existsBySlotName(request.getSlotName())) {
            throw new RuntimeException("Tên khung giờ \"" + request.getSlotName() + "\" đã được sử dụng!");
        }

        slot.setSlotName(request.getSlotName());
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        slot.setDayOfWeek(request.getDayOfWeek());
        return mapToResponse(timeSlotRepository.save(slot));
    }

    /**
     * Xóa mềm một TimeSlot (chỉ đánh dấu isDeleted vì BaseEntity có cờ này).
     * Nếu muốn xóa cứng, gọi thẳng repository.deleteById().
     */
    @Transactional
    public void delete(String id) {
        TimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khung giờ với ID: " + id));
        slot.setDeleted(true);
        timeSlotRepository.save(slot);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private TimeSlotResponse mapToResponse(TimeSlot slot) {
        String dayLabel = slot.getDayOfWeek() != null
                ? DAY_LABELS.getOrDefault(slot.getDayOfWeek(), "Ngày " + slot.getDayOfWeek())
                : "Tất cả các ngày";

        return TimeSlotResponse.builder()
                .id(slot.getId())
                .slotName(slot.getSlotName())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .dayOfWeek(slot.getDayOfWeek())
                .dayLabel(dayLabel)
                .build();
    }
}
