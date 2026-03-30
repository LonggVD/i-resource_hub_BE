package com.example.i_resource_hub.service;

import com.example.i_resource_hub.entity.Category;
import com.example.i_resource_hub.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;

    // 1. LẤY DANH SÁCH DANH MỤC
    public List<Category> getActiveCategories() {
        return repository.findByDeletedFalse();
    }

    // 2. XEM CHI TIẾT
    public Category getCategoryById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục với ID này!"));
    }

    // 3. THÊM MỚI
    @Transactional
    public Category createCategory(Category category) {
        if (category.getCategoryName() == null || category.getCategoryName().trim().isEmpty()) {
            throw new RuntimeException("Lỗi: Tên danh mục không được để trống!");
        }

        if (category.getParent() != null && category.getParent().getId() != null) {
            Category realParent = repository.findById(category.getParent().getId())
                    .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục cha!"));
            category.setParent(realParent);
        }
        // -----------------------------

        category.setDeleted(false);
        return repository.save(category);
    }

    // 4. SỬA
    @Transactional
    public Category updateCategory(String id, Category categoryDetails) {
        Category existingCategory = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục để cập nhật!"));

        if (categoryDetails.getCategoryName() == null || categoryDetails.getCategoryName().trim().isEmpty()) {
            throw new RuntimeException("Lỗi: Tên danh mục không được để trống!");
        }

        existingCategory.setCategoryName(categoryDetails.getCategoryName());

        if (categoryDetails.getDescription() != null) {
            existingCategory.setDescription(categoryDetails.getDescription());
        }

        if (categoryDetails.getParent() != null && categoryDetails.getParent().getId() != null) {
            Category realParent = repository.findById(categoryDetails.getParent().getId())
                    .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục cha!"));
            existingCategory.setParent(realParent);
        } else {
            existingCategory.setParent(null);
        }
        // ---------------------------------------

        return repository.save(existingCategory);
    }

    // 5. XÓA MỀM
    @Transactional
    public void deleteCategory(String id) {
        Category existingCategory = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục để xóa!"));

        if (existingCategory.isDeleted()) {
            throw new RuntimeException("Lỗi: Danh mục này đã bị xóa từ trước!");
        }

        existingCategory.setDeleted(true);
        repository.save(existingCategory);
    }

    //khôi phục danh mục đã bị xoá
    public void restoreCategory(String id) {
        Category existingCategory = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy danh mục để khôi phục!"));

        if (!existingCategory.isDeleted()) {
            throw new RuntimeException("Lỗi: Danh mục này chưa bị xoá!");
        }

        existingCategory.setDeleted(false);
        repository.save(existingCategory);
    }

    @Transactional
    public List<Category> getDeletedCategories() {
        return repository.findByDeletedTrue();
    }

    //Cài đặt xoá cứng khỏi db
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void permanentlyDeleteSoftDeletedCategories() {
        System.out.println("Bắt đầu quá trình xoá cứng các danh mục đã bị xóa mềm...");
        LocalDateTime twoMonthsAgo = LocalDateTime.now().minusMonths(2);
        repository.deleteCategoryOldRecords(twoMonthsAgo);
        System.out.println("Đã hoàn thành quá trình xoá cứng các danh mục đã bị xóa mềm!");
    }
}