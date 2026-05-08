package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    boolean existsByStudentCode(String studentCode);

    /**
     * Tìm Manager / Admin còn ACTIVE thuộc 1 đơn vị (để gửi notification khi có đơn mượn mới).
     * Bao gồm: ROLE_MANAGER trong unit + ROLE_ADMIN (super-admin nhận tất cả).
     */
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r " +
            "WHERE u.status = 'ACTIVE' " +
            "AND ((u.unit.id = :unitId AND r.roleCode = 'MANAGER') OR r.roleCode = 'ADMIN')")
    List<User> findManagersAndAdminsByUnitId(@Param("unitId") String unitId);
}

