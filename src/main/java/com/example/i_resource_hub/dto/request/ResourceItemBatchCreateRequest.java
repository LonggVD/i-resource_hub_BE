package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemBatchCreateRequest {
    @NotNull(message = "Template ID is required")
    private String templateId;

    @NotEmpty(message = "Serial numbers list cannot be empty")
    private List<String> serialNumbers;

    private String conditionStatus;
    private String status;
    private String unitId;
}
