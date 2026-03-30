package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.OrganizationUnitCreateRequest;
import com.example.i_resource_hub.dto.request.OrganizationUnitUpdateRequest;
import com.example.i_resource_hub.dto.response.OrganizationUnitResponse;
import com.example.i_resource_hub.service.OrganizationUnitService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organization-units")
public class OrganizationUnitController {

    private final OrganizationUnitService organizationUnitService;

    @GetMapping
    @Operation(summary = "Get All Unit", description = "Lấy danh sách tổ chức ")
    public ResponseEntity<List<OrganizationUnitResponse>> getAllUnits() {
        return ResponseEntity.ok(organizationUnitService.getAllUnits());
    }

    @PostMapping
    @Operation(summary = "Create Unit", description = "Tạo một tổ chức mới ")
    public ResponseEntity<OrganizationUnitResponse> createUnit(@Valid @RequestBody OrganizationUnitCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationUnitService.createUnit(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Unit", description = "Thay đổi thông tin tổ chức ")
    public ResponseEntity<OrganizationUnitResponse> updateUnit(
            @PathVariable String id,
            @Valid @RequestBody OrganizationUnitUpdateRequest request
    ) {
        return ResponseEntity.ok(organizationUnitService.updateUnit(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft Delete", description = "Xoá mêm tổ chức (đánh dấu xoá) ")
    public ResponseEntity<Void> deleteUnit(@PathVariable String id) {
        organizationUnitService.deleteUnit(id);
        return ResponseEntity.noContent().build();
    }
}
