package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.OrganizationUnitCreateRequest;
import com.example.i_resource_hub.dto.request.OrganizationUnitUpdateRequest;
import com.example.i_resource_hub.dto.response.OrganizationUnitResponse;
import com.example.i_resource_hub.entity.OrganizationUnit;
import com.example.i_resource_hub.repository.OrganizationUnitRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationUnitService {

    private final OrganizationUnitRepository organizationUnitRepository;

    public List<OrganizationUnitResponse> getAllUnits() {
        return organizationUnitRepository.findAllByDeletedAtIsNull()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrganizationUnitResponse createUnit(OrganizationUnitCreateRequest request) {
        OrganizationUnit parent = null;
        if (request.getParentId() != null && !request.getParentId().isBlank()) {
            parent = findActiveById(request.getParentId());
        }

        OrganizationUnit unit = OrganizationUnit.builder()
                .parent(parent)
                .unitName(request.getUnitName())
                .unitType(request.getUnitType())
                .build();

        OrganizationUnit saved = organizationUnitRepository.save(unit);
        return toResponse(saved);
    }

    @Transactional
    public OrganizationUnitResponse updateUnit(String id, OrganizationUnitUpdateRequest request) {
        OrganizationUnit unit = findActiveById(id);
        unit.setUnitName(request.getUnitName());
        unit.setUnitType(request.getUnitType());

        OrganizationUnit saved = organizationUnitRepository.save(unit);
        return toResponse(saved);
    }

    @Transactional
    public void deleteUnit(String id) {
        OrganizationUnit unit = findActiveById(id);
        unit.setDeletedAt(LocalDateTime.now());
        unit.setDeleted(true);
        organizationUnitRepository.save(unit);
    }

    private OrganizationUnit findActiveById(String id) {
        OrganizationUnit unit = organizationUnitRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization unit not found with id: " + id));

        if (unit.getDeletedAt() != null) {
            throw new EntityNotFoundException("Organization unit not found with id: " + id);
        }
        return unit;
    }

    private OrganizationUnitResponse toResponse(OrganizationUnit unit) {
        return OrganizationUnitResponse.builder()
                .id(unit.getId())
                .parentId(unit.getParent() != null ? unit.getParent().getId() : null)
                .unitName(unit.getUnitName())
                .unitType(unit.getUnitType())
                .createdAt(unit.getCreatedAt())
                .updatedAt(unit.getUpdatedAt())
                .build();
    }
}
