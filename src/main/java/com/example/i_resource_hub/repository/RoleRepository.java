package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role,Integer> {
    // tìm theo mã vai trò
    Role findByRoleCode(String roleCode);
}
