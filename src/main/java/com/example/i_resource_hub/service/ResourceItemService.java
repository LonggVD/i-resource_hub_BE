package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.ResourceItemBatchCreateRequest;
import com.example.i_resource_hub.dto.request.ResourceItemCreateRequest;
import com.example.i_resource_hub.dto.request.ResourceItemUpdateRequest;
import com.example.i_resource_hub.dto.response.ResourceItemResponse;
import com.example.i_resource_hub.entity.OrganizationUnit;
import com.example.i_resource_hub.entity.ResourceItem;
import com.example.i_resource_hub.entity.ResourceTemplate;
import com.example.i_resource_hub.repository.OrganizationUnitRepository;
import com.example.i_resource_hub.repository.ResourceItemRepository;
import com.example.i_resource_hub.repository.ResourceTemplateRepository;
import com.example.i_resource_hub.repository.specification.ResourceItemSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceItemService {

    private final ResourceItemRepository resourceItemRepository;
    private final ResourceTemplateRepository resourceTemplateRepository;
    private final OrganizationUnitRepository organizationUnitRepository;

    @Transactional(readOnly = true)
    public List<ResourceItemResponse> getAllActive() {
        return resourceItemRepository.findAllByIsDeletedFalse().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ResourceItemResponse> filter(String templateId, String unitId, String status,
                                             String conditionStatus, String keyword) {
        Specification<ResourceItem> spec = ResourceItemSpecification.filter(
                templateId, unitId, status, conditionStatus, keyword);
        return resourceItemRepository.findAll(spec).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ResourceItemResponse getById(String id) {
        ResourceItem item = resourceItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thiết bị với ID: " + id));
        return mapToResponse(item);
    }

    @Transactional(readOnly = true)
    public ResourceItemResponse getBySerialNumber(String serialNumber) {
        ResourceItem item = resourceItemRepository.findBySerialNumber(serialNumber)
                .orElseThrow(
                        () -> new EntityNotFoundException("Không tìm thấy thiết bị với số Serial: " + serialNumber));
        return mapToResponse(item);
    }

    @Transactional
    public ResourceItemResponse create(ResourceItemCreateRequest request) {
        if (resourceItemRepository.findBySerialNumber(request.getSerialNumber()).isPresent()) {
            throw new RuntimeException("Số Serial đã tồn tại trên hệ thống!");
        }

        ResourceTemplate template = resourceTemplateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy mẫu tài nguyên!"));

        ResourceItem item = ResourceItem.builder()
                .template(template)
                .serialNumber(request.getSerialNumber())
                .purchaseDate(request.getPurchaseDate())
                .warrantyExpiry(request.getWarrantyExpiry())
                .conditionStatus(request.getConditionStatus() != null ? request.getConditionStatus() : "GOOD")
                .status(request.getStatus() != null ? request.getStatus() : "AVAILABLE")
                .build();

        if (request.getUnitId() != null) {
            item.setManagedByUnit(organizationUnitRepository.findById(request.getUnitId()).orElse(null));
        }

        return mapToResponse(resourceItemRepository.save(item));
    }

    @Transactional
    public List<ResourceItemResponse> batchCreate(ResourceItemBatchCreateRequest request) {
        ResourceTemplate template = resourceTemplateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy mẫu tài nguyên!"));

        List<ResourceItem> items = new ArrayList<>();
        for (String sn : request.getSerialNumbers()) {
            if (resourceItemRepository.findBySerialNumber(sn).isPresent()) {
                throw new RuntimeException("Số Serial " + sn + " đã tồn tại!");
            }

            ResourceItem item = ResourceItem.builder()
                    .template(template)
                    .serialNumber(sn)
                    .conditionStatus(request.getConditionStatus() != null ? request.getConditionStatus() : "GOOD")
                    .status(request.getStatus() != null ? request.getStatus() : "AVAILABLE")
                    .build();

            if (request.getUnitId() != null) {
                item.setManagedByUnit(organizationUnitRepository.findById(request.getUnitId()).orElse(null));
            }
            items.add(item);
        }

        return resourceItemRepository.saveAll(items).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ResourceItemResponse update(String id, ResourceItemUpdateRequest request) {
        ResourceItem item = resourceItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thiết bị!"));

        if (request.getSerialNumber() != null && !request.getSerialNumber().equals(item.getSerialNumber())) {
            if (resourceItemRepository.findBySerialNumber(request.getSerialNumber()).isPresent()) {
                throw new RuntimeException("Số Serial đã được sử dụng bởi thiết bị khác!");
            }
            item.setSerialNumber(request.getSerialNumber());
        }

        if (request.getPurchaseDate() != null)
            item.setPurchaseDate(request.getPurchaseDate());
        if (request.getWarrantyExpiry() != null)
            item.setWarrantyExpiry(request.getWarrantyExpiry());
        if (request.getConditionStatus() != null)
            item.setConditionStatus(request.getConditionStatus());
        if (request.getStatus() != null)
            item.setStatus(request.getStatus());

        return mapToResponse(resourceItemRepository.save(item));
    }

    @Transactional
    public void softDelete(String id) {
        ResourceItem item = resourceItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thiết bị!"));
        item.setDeleted(true);
        resourceItemRepository.save(item);
    }

    @Transactional
    public void restore(String id) {
        ResourceItem item = resourceItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thiết bị!"));
        item.setDeleted(false);
        resourceItemRepository.save(item);
    }

    private ResourceItemResponse mapToResponse(ResourceItem item) {
        ResourceItemResponse.TemplateSummary templateSummary = null;
        if (item.getTemplate() != null) {
            templateSummary = ResourceItemResponse.TemplateSummary.builder()
                    .id(item.getTemplate().getId())
                    .name(item.getTemplate().getName())
                    .imageUrl(item.getTemplate().getImageUrl())
                    .build();
        }

        ResourceItemResponse.UnitSummary unitSummary = null;
        // Ưu tiên lấy đơn vị trực tiếp từ Item, nếu không có thì lấy từ Template
        OrganizationUnit unit = item.getManagedByUnit() != null ? item.getManagedByUnit()
                : (item.getTemplate() != null ? item.getTemplate().getUnit() : null);

        if (unit != null) {
            unitSummary = ResourceItemResponse.UnitSummary.builder()
                    .id(unit.getId())
                    .unitName(unit.getUnitName())
                    .build();
        }

        return ResourceItemResponse.builder()
                .id(item.getId())
                .template(templateSummary)
                .unit(unitSummary)
                .serialNumber(item.getSerialNumber())
                .purchaseDate(item.getPurchaseDate())
                .warrantyExpiry(item.getWarrantyExpiry())
                .conditionStatus(item.getConditionStatus())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

}
