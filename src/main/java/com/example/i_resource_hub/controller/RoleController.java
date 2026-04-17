package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.UpdateRolePermissionsRequest;
import com.example.i_resource_hub.dto.response.PermissionResponse;
import com.example.i_resource_hub.dto.response.RoleDetailResponse;
import com.example.i_resource_hub.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<List<RoleDetailResponse>> getAllRoles() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<List<PermissionResponse>> getAllPermissions() {
        return ResponseEntity.ok(roleService.getAllPermissions());
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<RoleDetailResponse> updateRolePermissions(
            @PathVariable String id,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ResponseEntity.ok(roleService.updateRolePermissions(id, request));
    }
}
