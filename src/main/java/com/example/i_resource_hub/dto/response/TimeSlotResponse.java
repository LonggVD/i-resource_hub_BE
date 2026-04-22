package com.example.i_resource_hub.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Data
@Builder
public class TimeSlotResponse {
    private String id;
    private String slotName;
    private LocalTime startTime;
    private LocalTime endTime;

    /**
     * Ngày trong tuần (1 = Thứ Hai ... 7 = Chủ Nhật), null = áp dụng mọi ngày.
     */
    private Integer dayOfWeek;

    /**
     * Nhãn hiển thị thân thiện, ví dụ "Thứ Hai" hoặc "Tất cả các ngày".
     */
    private String dayLabel;
}
