package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemCreateRequest {
    @NotBlank(message = "Template ID is required")
    private String templateId;

    @NotBlank(message = "Serial number is required")
    private String serialNumber;

    private LocalDate purchaseDate;
    private LocalDate warrantyExpiry;
    private String conditionStatus;
    private String status;
    private String unitId;
}
