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
public class EvidenceResponse {
    private String id;
    private String bookingId;
    private String resourceItemId;
    private String evidenceType;
    private String imageUrl;
    private String description;
    private String resolution;
    private Boolean isResolved;
    private String createdBy;
    private LocalDateTime createdAt;
    
    private String borrowerName;
    private String borrowerId;
    private String userId;
    private String serialNumber;
    private String deviceName;
    private String ownerUnitName;
}
