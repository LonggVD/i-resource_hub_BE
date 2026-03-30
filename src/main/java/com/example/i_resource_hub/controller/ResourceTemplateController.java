package com.example.i_resource_hub.controller;
import com.example.i_resource_hub.dto.request.ResourceTemplateCreateRequest;
import com.example.i_resource_hub.dto.request.ResourceTemplateUpdateRequest;
import com.example.i_resource_hub.dto.response.ResourceTemplateResponse;
import com.example.i_resource_hub.service.ResourceTemplateService;
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
@RequestMapping("/api/resource-templates")
public class ResourceTemplateController {
    private final ResourceTemplateService resourceTemplateService;
    @GetMapping
    @Operation(summary = "Get active resource templates")
    public ResponseEntity<List<ResourceTemplateResponse>> getAllActive() {
        return ResponseEntity.ok(resourceTemplateService.getAllActive());
    }
    @GetMapping("/deleted")
    @Operation(summary = "Get deleted resource templates")
    public ResponseEntity<List<ResourceTemplateResponse>> getAllDeleted() {
        return ResponseEntity.ok(resourceTemplateService.getAllDeleted());
    }
    @GetMapping("/{id}")
    @Operation(summary = "Get resource template by id")
    public ResponseEntity<ResourceTemplateResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(resourceTemplateService.getById(id));
    }
    @PostMapping
    @Operation(summary = "Create resource template")
    public ResponseEntity<ResourceTemplateResponse> create(@Valid @RequestBody ResourceTemplateCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resourceTemplateService.create(request));
    }
    @PutMapping("/{id}")
    @Operation(summary = "Update resource template")
    public ResponseEntity<ResourceTemplateResponse> update(
            @PathVariable String id,
            @Valid @RequestBody ResourceTemplateUpdateRequest request
    ) {
        return ResponseEntity.ok(resourceTemplateService.update(id, request));
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete resource template")
    public ResponseEntity<Void> softDelete(@PathVariable String id) {
        resourceTemplateService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}/restore")
    @Operation(summary = "Restore soft deleted resource template")
    public ResponseEntity<Void> restore(@PathVariable String id) {
        resourceTemplateService.restore(id);
        return ResponseEntity.noContent().build();
    }
}