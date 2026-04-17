package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.ResourceItemBatchCreateRequest;
import com.example.i_resource_hub.dto.request.ResourceItemCreateRequest;
import com.example.i_resource_hub.dto.request.ResourceItemUpdateRequest;
import com.example.i_resource_hub.dto.response.ResourceItemResponse;
import com.example.i_resource_hub.service.ResourceItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resource-items")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ResourceItemController {

    private final ResourceItemService resourceItemService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESOURCE_VIEW')")
    public ResponseEntity<List<ResourceItemResponse>> getAllActive() {
        return ResponseEntity.ok(resourceItemService.getAllActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RESOURCE_VIEW')")
    public ResponseEntity<ResourceItemResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(resourceItemService.getById(id));
    }

    @GetMapping("/scan/{serialNumber}")
    @PreAuthorize("hasAuthority('RESOURCE_VIEW')")
    public ResponseEntity<ResourceItemResponse> getBySerialNumber(@PathVariable String serialNumber) {
        return ResponseEntity.ok(resourceItemService.getBySerialNumber(serialNumber));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<ResourceItemResponse> create(@Valid @RequestBody ResourceItemCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resourceItemService.create(request));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<List<ResourceItemResponse>> batchCreate(@Valid @RequestBody ResourceItemBatchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resourceItemService.batchCreate(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<ResourceItemResponse> update(@PathVariable String id, @Valid @RequestBody ResourceItemUpdateRequest request) {
        return ResponseEntity.ok(resourceItemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<Void> softDelete(@PathVariable String id) {
        resourceItemService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<Void> restore(@PathVariable String id) {
        resourceItemService.restore(id);
        return ResponseEntity.ok().build();
    }
}
