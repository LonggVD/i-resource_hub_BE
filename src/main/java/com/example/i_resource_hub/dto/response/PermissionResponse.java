package com.example.i_resource_hub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {
    private String id;
    private String permissionCode;
    private String resourceCode;
    private String actionCode;
    private String description;
}
