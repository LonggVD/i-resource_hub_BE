package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.OrganizationUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, String> {
        boolean existsByUnitName(String name);
}
