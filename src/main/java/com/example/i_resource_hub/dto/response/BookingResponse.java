package com.example.i_resource_hub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private String id;
    private String userId;
    private String borrowerName;
    private String borrowerId; // Mã sinh viên
    private String borrowerUnitName;
    private String resourceItemId;
    private String deviceName;
    private String serialNumber;
    private LocalDate bookingDate;
    private String slotId;
    private String slotName;
    private String startTime; // LocalTime string
    private String endTime; // LocalTime string
    private String status;
    private String qrCodeToken;
    private String purpose;
    private String batchToken;
    private boolean expired;
    private String ownerUnitId;
    private String ownerUnitName;
    private Boolean hasDamage;
    private String damageDescription;
    private String resolution;
    private Boolean isResolved;
    private String evidenceImageUrl;
    private Boolean isPenalized;
}
