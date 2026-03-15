package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    // tìm theo mã vai trò
    Optional<Role> findByRoleCode(String roleCode);
}
