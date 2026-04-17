package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.UpdateRolePermissionsRequest;
import com.example.i_resource_hub.dto.response.PermissionResponse;
import com.example.i_resource_hub.dto.response.RoleDetailResponse;
import com.example.i_resource_hub.entity.Permission;
import com.example.i_resource_hub.entity.Role;
import com.example.i_resource_hub.repository.PermissionRepository;
import com.example.i_resource_hub.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public List<RoleDetailResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToRoleDetailResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleDetailResponse updateRolePermissions(String roleId, UpdateRolePermissionsRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy vai trò với ID: " + roleId));

        if (role.getIsSystem() != null && role.getIsSystem() && "ADMIN".equals(role.getRoleCode())) {
            // Không cho phép chỉnh sửa quyền của Super Admin (nếu muốn khóa)
            // throw new RuntimeException("Không được phép chỉnh sửa quyền của quản trị viên hệ thống.");
        }

        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
        role.setPermissions(permissions);
        
        return mapToRoleDetailResponse(roleRepository.save(role));
    }

    private RoleDetailResponse mapToRoleDetailResponse(Role role) {
        return RoleDetailResponse.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .status(role.getStatus())
                .permissions(role.getPermissions() != null ? 
                        role.getPermissions().stream().map(this::mapToPermissionResponse).collect(Collectors.toList()) : null)
                .build();
    }

    private PermissionResponse mapToPermissionResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .permissionCode(permission.getPermissionCode())
                .resourceCode(permission.getResourceCode())
                .actionCode(permission.getActionCode())
                .description(permission.getDescription())
                .build();
    }
}
