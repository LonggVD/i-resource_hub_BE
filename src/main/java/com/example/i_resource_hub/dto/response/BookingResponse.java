package com.example.i_resource_hub.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BookingResponse {
    private String id;
    private String userId;
    private String borrowerName;
    private String borrowerId; // Mã sinh viên
    private String resourceItemId;
    private String deviceName;
    private String serialNumber;
    private LocalDate bookingDate;
    private String slotId;
    private String slotName;
    private String startTime; // LocalTime string
    private String endTime;   // LocalTime string
    private String status;
    private String qrCodeToken;
    private String purpose;
}
