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
public class NotificationResponse {
    private String id;
    private String userId;
    private String type;          // BOOKING_APPROVED, BOOKING_REJECTED, BOOKING_CANCELLED, PENALTY_CREATED...
    private String referenceId;   // bookingId / penaltyId tương ứng để FE deep-link
    private String title;
    private String content;
    private boolean read;         // tính từ readAt != null
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
