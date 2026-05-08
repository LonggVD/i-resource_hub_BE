package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.CategoryRequest;
import com.example.i_resource_hub.dto.response.CategoryResponse;
import com.example.i_resource_hub.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService service;

    // 1. Lấy danh sách tất cả các category chưa xoá
    @GetMapping
    @Operation(summary = "get all", description = "Lấy danh sách danh mục chưa bị xoá")
    public ResponseEntity<List<CategoryResponse>> findAll() {
        return ResponseEntity.ok(service.getActiveCategories());
    }

    // 2. Lấy danh sách tất cả category đã xoá (Thùng rác)
    @GetMapping("/deleted")
    @Operation(summary = "get all deleted", description = "Lấy danh sách danh mục trong thùng rác")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<List<CategoryResponse>> findAllDeleted() {
        return ResponseEntity.ok(service.getDeletedCategories());
    }

    // 3. Xem chi tiết
    @GetMapping("/{id}")
    @Operation(summary = "details", description = "Xem chi tiết danh mục")
    public ResponseEntity<CategoryResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.getCategoryById(id));
    }

    // 4. Thêm danh mục mới
    @PostMapping
    @Operation(summary = "create", description = "Thêm danh mục mới")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCategory(request));
    }

    // 5. Chỉnh sửa danh mục
    @PutMapping("/{id}")
    @Operation(summary = "update", description = "Cập nhật danh mục")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<CategoryResponse> update(@PathVariable String id,
                                                   @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(service.updateCategory(id, request));
    }

    // 6. Xoá mềm bản ghi
    @DeleteMapping("/{id}")
    @Operation(summary = "delete", description = "Xoá mềm bản ghi")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // 7. Khôi phục bản ghi đã bị xoá mềm
    @PutMapping("/{id}/restore")
    @Operation(summary = "restore", description = "Khôi phục bản ghi đã bị xoá mềm")
    @PreAuthorize("hasAuthority('RESOURCE_MANAGE')")
    public ResponseEntity<Void> restoreCategory(@PathVariable String id) {
        service.restoreCategory(id);
        return ResponseEntity.ok().build();
    }
}
