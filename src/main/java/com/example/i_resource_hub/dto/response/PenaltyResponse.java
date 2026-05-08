package com.example.i_resource_hub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PenaltyResponse {
    private String id;
    private String userId;
    private String studentCode;
    private String studentName;
    private String bookingId;
    private String penaltyType;
    private Integer penaltyPoint;
    private String description;
    private String status;
    private String createdByName;
    private LocalDateTime createdAt;

    // Thông tin tổng hợp của user (dùng cho cột phụ trong UI)
    private Integer currentCreditScore;
    private String userStatus;
    private long totalActivePenalties;
    private java.util.List<EvidenceResponse> evidences;
    private Double fineAmount;
    private Boolean requiresReview;
    private String reviewStatus;

    // Thông tin đơn mượn liên quan để hiển thị cho SV
    private String bookingBatchToken;
    private java.time.LocalDate bookingDate;
    private String bookingSlot;
    private String bookingDeviceName;
    private String thumbnailUrl;
}
