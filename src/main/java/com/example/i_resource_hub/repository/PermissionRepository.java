package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    Optional<Permission> findByPermissionCode(String permissionCode);
}
