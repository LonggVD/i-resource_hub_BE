package com.example.i_resource_hub.service;
import com.example.i_resource_hub.dto.request.ResourceTemplateCreateRequest;
import com.example.i_resource_hub.dto.request.ResourceTemplateUpdateRequest;
import com.example.i_resource_hub.dto.response.ResourceTemplateResponse;
import com.example.i_resource_hub.entity.Category;
import com.example.i_resource_hub.entity.OrganizationUnit;
import com.example.i_resource_hub.entity.ResourceTemplate;
import com.example.i_resource_hub.repository.CategoryRepository;
import com.example.i_resource_hub.repository.OrganizationUnitRepository;
import com.example.i_resource_hub.repository.ResourceTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceTemplateService {
    private final ResourceTemplateRepository resourceTemplateRepository;
    private final CategoryRepository categoryRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    public List<ResourceTemplateResponse> getAllActive() {
        return resourceTemplateRepository.findAllByIsDeletedFalse()
                .stream()
                .map(this::toResponse)
                .toList();
    }
    public List<ResourceTemplateResponse> getAllDeleted() {
        return resourceTemplateRepository.findAllByIsDeletedTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }
    public ResourceTemplateResponse getById(String id) {
        ResourceTemplate template = resourceTemplateRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Resource template not found with id: " + id));
        return toResponse(template);
    }
    @Transactional
    public ResourceTemplateResponse create(ResourceTemplateCreateRequest request) {
        Category category = resolveCategory(request.getCategoryId());
        OrganizationUnit unit = resolveUnit(request.getUnitId());
        ResourceTemplate template = ResourceTemplate.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isAutoApprove(request.getIsAutoApprove() != null ? request.getIsAutoApprove() : Boolean.FALSE)
                .imageUrl(request.getImageUrl())
                .category(category)
                .unit(unit)
                .build();
        return toResponse(resourceTemplateRepository.save(template));
    }
    @Transactional
    public ResourceTemplateResponse update(String id, ResourceTemplateUpdateRequest request) {
        ResourceTemplate template = resourceTemplateRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Resource template not found with id: " + id));
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setIsAutoApprove(request.getIsAutoApprove() != null ? request.getIsAutoApprove() : Boolean.FALSE);
        template.setImageUrl(request.getImageUrl());
        template.setCategory(resolveCategory(request.getCategoryId()));
        template.setUnit(resolveUnit(request.getUnitId()));
        return toResponse(resourceTemplateRepository.save(template));
    }
    @Transactional
    public void softDelete(String id) {
        ResourceTemplate template = resourceTemplateRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Resource template not found with id: " + id));
        template.setDeleted(true);
        resourceTemplateRepository.save(template);
    }
    @Transactional
    public void restore(String id) {
        ResourceTemplate template = resourceTemplateRepository.findByIdAndIsDeletedTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("Deleted resource template not found with id: " + id));
        template.setDeleted(false);
        resourceTemplateRepository.save(template);
    }
    private Category resolveCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + categoryId));
        if (category.isDeleted()) {
            throw new EntityNotFoundException("Category not found with id: " + categoryId);
        }
        return category;
    }
    private OrganizationUnit resolveUnit(String unitId) {
        if (unitId == null || unitId.isBlank()) {
            return null;
        }
        OrganizationUnit unit = organizationUnitRepository.findById(unitId)
                .orElseThrow(() -> new EntityNotFoundException("Organization unit not found with id: " + unitId));
        if (unit.isDeleted() || unit.getDeletedAt() != null) {
            throw new EntityNotFoundException("Organization unit not found with id: " + unitId);
        }
        return unit;
    }
    private ResourceTemplateResponse toResponse(ResourceTemplate template) {
        ResourceTemplateResponse.CategorySummary categorySummary = null;
        if (template.getCategory() != null) {
            categorySummary = ResourceTemplateResponse.CategorySummary.builder()
                    .id(template.getCategory().getId())
                    .categoryName(template.getCategory().getCategoryName())
                    .build();
        }
        ResourceTemplateResponse.OrganizationUnitSummary unitSummary = null;
        if (template.getUnit() != null) {
            unitSummary = ResourceTemplateResponse.OrganizationUnitSummary.builder()
                    .id(template.getUnit().getId())
                    .unitName(template.getUnit().getUnitName())
                    .build();
        }
        return ResourceTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .isAutoApprove(template.getIsAutoApprove())
                .imageUrl(template.getImageUrl())
                .category(categorySummary)
                .unit(unitSummary)
                .build();
    }

    //càidđặt xoá cứng khỏi DB
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void permanentlyDeleteSoftDeletedRT() {
        System.out.println("Bắt đầu quá trình xoá cứng các danh mục đã bị xóa mềm...");
        LocalDateTime twoMonthsAgo = LocalDateTime.now().minusMonths(2);
        resourceTemplateRepository.deleteRTOldRecords(twoMonthsAgo);
        System.out.println("Đã hoàn thành quá trình xoá cứng các danh mục đã bị xóa mềm!");
    }
}