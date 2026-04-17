package com.example.i_resource_hub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDetailResponse {
    private String id;
    private String roleCode;
    private String roleName;
    private String description;
    private Boolean isSystem;
    private String status;
    private List<PermissionResponse> permissions;
}
