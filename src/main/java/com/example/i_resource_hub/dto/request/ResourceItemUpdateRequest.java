package com.example.i_resource_hub.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemUpdateRequest {
    private String serialNumber;
    private LocalDate purchaseDate;
    private LocalDate warrantyExpiry;
    private String conditionStatus;
    private String status;
}
