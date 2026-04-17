package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class UpdateRolePermissionsRequest {
    @NotNull(message = "Danh sách quyền không được để trống")
    private List<String> permissionIds;
}
