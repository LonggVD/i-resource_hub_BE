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
public class OrganizationUnitResponse {
    private String id;
    private String parentId;
    private String unitName;
    private String unitType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
