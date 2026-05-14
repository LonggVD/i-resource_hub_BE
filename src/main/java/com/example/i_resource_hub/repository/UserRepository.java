package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Atomic deduct creditScore — tránh race condition khi nhiều penalty cùng tạo song song.
     * SQL native: dùng GREATEST(.., 0) đảm bảo score không âm.
     * Đồng thời tự động set status='LOCKED' nếu score sau khi trừ về 0.
     * @return số dòng bị ảnh hưởng (1 nếu thành công, 0 nếu user không tồn tại).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE users SET " +
            "credit_score = GREATEST(credit_score - :points, 0), " +
            "status = CASE WHEN credit_score - :points <= 0 THEN 'LOCKED' ELSE status END " +
            "WHERE id = :userId",
            nativeQuery = true)
    int deductCreditScore(@Param("userId") String userId, @Param("points") int points);

    /**
     * Atomic restore creditScore — dùng khi revoke penalty.
     * Giới hạn tối đa 100 điểm.
     * Đồng thời tự động unlock account (status='ACTIVE') nếu score sau khi cộng > 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE users SET " +
            "credit_score = LEAST(credit_score + :points, 100), " +
            "status = CASE WHEN credit_score + :points > 0 AND status = 'LOCKED' THEN 'ACTIVE' ELSE status END " +
            "WHERE id = :userId",
            nativeQuery = true)
    int restoreCreditScore(@Param("userId") String userId, @Param("points") int points);
}
