package com.example.i_resource_hub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemResponse {
    private String id;
    private TemplateSummary template;
    private String serialNumber;
    private LocalDate purchaseDate;
    private LocalDate warrantyExpiry;
    private String conditionStatus;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateSummary {
        private String id;
        private String name;
        private String imageUrl;
    }
}
