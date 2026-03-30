package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.entity.Category;
import com.example.i_resource_hub.service.CategoryService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*", maxAge = 3600)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CategoryController {

    private final CategoryService service;

    // 1. Lấy danh sách tất cả các category chưa xoá
    @GetMapping
    @Operation(summary = "get all", description = "Lấy danh sách danh mục chưa bị xoá ")
    public ResponseEntity<List<Category>> findAll() {
        return ResponseEntity.ok(service.getActiveCategories());
    }

    // 2. Lấy danh sách tất cả category đã xoá (Thùng rác)
    @GetMapping("/deleted")
    @Operation(summary = "get all deleted", description = "Lấy danh sách danh mục trong thùng rác ")
    public ResponseEntity<List<Category>> findAllDeleted() {
        return ResponseEntity.ok(service.getDeletedCategories());
    }

    // 3. Xem chi tiết
    @GetMapping("/{id}")
    @Operation(summary = "details", description = "xem chi tiết danh mujc ")
    public ResponseEntity<Category> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.getCategoryById(id));
    }

    // 4. Thêm danh mục mới
    @PostMapping
    @Operation(summary = "create", description = "Thêm danh mục mơi ")
    public ResponseEntity<Category> create(@RequestBody Category category) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCategory(category));
    }

    // 5. Chỉnh sửa danh mục
    @PutMapping("/{id}")
    @Operation(summary = "update", description = "Cập nhật danh mục ")
    public ResponseEntity<Category> update(@PathVariable String id, @RequestBody Category category) {
        return ResponseEntity.ok(service.updateCategory(id, category));

    }
    // 6. Xoá mềm bản ghi (Dùng DeleteMapping)
    @DeleteMapping("/{id}")
    @Operation(summary = "delete mapping", description = "Xoá mềm bản ghi ")
    public ResponseEntity<String> deleteCategory(@PathVariable String id) {
        service.deleteCategory(id);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    // 7. Khôi phục bản ghi đã bị xoá mềm
    @PutMapping("/{id}/restore")
    @Operation(summary = "restore", description = "Khôi phục bản ghi đã bị xoá mềm ")
    public ResponseEntity<String> restoreCategory(@PathVariable String id) {
        service.restoreCategory(id);
        return ResponseEntity.ok().build();
    }
}