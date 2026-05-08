package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.CategoryRequest;
import com.example.i_resource_hub.dto.response.CategoryResponse;
import com.example.i_resource_hub.entity.Category;
import com.example.i_resource_hub.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;

    // 1. LẤY DANH SÁCH DANH MỤC ĐANG HOẠT ĐỘNG
    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return repository.findByDeletedFalse().stream()
                .map(this::toResponse)
                .toList();
    }

    // 2. XEM CHI TIẾT
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(String id) {
        Category category = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục với ID này!"));
        return toResponse(category);
    }

    // 3. THÊM MỚI
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = Category.builder()
                .categoryName(request.getCategoryName().trim())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .build();

        if (request.getParentId() != null && !request.getParentId().isBlank()) {
            Category parent = repository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục cha!"));
            category.setParent(parent);
        }

        category.setDeleted(false);
        return toResponse(repository.save(category));
    }

    // 4. SỬA
    @Transactional
    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục để cập nhật!"));

        existing.setCategoryName(request.getCategoryName().trim());
        existing.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }

        if (request.getParentId() != null && !request.getParentId().isBlank()) {
            if (request.getParentId().equals(id)) {
                throw new RuntimeException("Lỗi: Danh mục không thể là cha của chính nó!");
            }
            Category parent = repository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục cha!"));
            existing.setParent(parent);
        } else {
            existing.setParent(null);
        }

        return toResponse(repository.save(existing));
    }

    // 5. XÓA MỀM
    @Transactional
    public void deleteCategory(String id) {
        Category existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục để xóa!"));

        if (existing.isDeleted()) {
            throw new RuntimeException("Lỗi: Danh mục này đã bị xóa từ trước!");
        }

        existing.setDeleted(true);
        repository.save(existing);
    }

    // 6. KHÔI PHỤC DANH MỤC ĐÃ XOÁ MỀM
    @Transactional
    public void restoreCategory(String id) {
        Category existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục để khôi phục!"));

        if (!existing.isDeleted()) {
            throw new RuntimeException("Lỗi: Danh mục này chưa bị xoá!");
        }

        existing.setDeleted(false);
        repository.save(existing);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getDeletedCategories() {
        return repository.findByDeletedTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    // Cài đặt xoá cứng khỏi DB sau 2 tháng
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void permanentlyDeleteSoftDeletedCategories() {
        log.info("Bắt đầu quá trình xoá cứng các danh mục đã bị xóa mềm...");
        LocalDateTime twoMonthsAgo = LocalDateTime.now().minusMonths(2);
        repository.deleteCategoryOldRecords(twoMonthsAgo);
        log.info("Đã hoàn thành quá trình xoá cứng các danh mục đã bị xóa mềm!");
    }

    // ===== Mapper =====
    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .description(category.getDescription())
                .status(category.getStatus())
                .deleted(category.isDeleted())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getCategoryName() : null)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
