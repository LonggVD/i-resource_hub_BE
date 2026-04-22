package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class TimeSlotRequest {

    /**
     * Tên ca học, ví dụ: "Ca 1 (07:00 – 09:15)"
     */
    @NotBlank(message = "Tên khung giờ không được để trống")
    private String slotName;

    /**
     * Thời điểm bắt đầu ca, định dạng HH:mm
     */
    @NotNull(message = "Giờ bắt đầu không được để trống")
    private LocalTime startTime;

    /**
     * Thời điểm kết thúc ca, định dạng HH:mm
     */
    @NotNull(message = "Giờ kết thúc không được để trống")
    private LocalTime endTime;

    /**
     * Ngày trong tuần áp dụng (1 = Thứ Hai, ..., 7 = Chủ Nhật).
     * Nếu null → ca áp dụng cho tất cả các ngày.
     */
    private Integer dayOfWeek;
}
