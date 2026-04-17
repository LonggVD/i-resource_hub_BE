package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.ResourceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceItemRepository extends JpaRepository<ResourceItem, String>, JpaSpecificationExecutor<ResourceItem> {
    Optional<ResourceItem> findBySerialNumber(String serialNumber);
    List<ResourceItem> findAllByTemplateIdAndIsDeletedFalse(String templateId);
    List<ResourceItem> findAllByIsDeletedFalse();
}
