package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.Penalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PenaltyRepository extends JpaRepository<Penalty, String> {

    List<Penalty> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId);

    List<Penalty> findByIsDeletedFalseOrderByCreatedAtDesc();

    /**
     * Lấy penalties theo phạm vi unit của manager. unitId null = admin (lấy tất cả).
     * Match nếu:
     *  - SV thuộc unit (logic gốc), HOẶC
     *  - Penalty gắn booking thuộc unit (managedByUnit / item.managedByUnit / template.unit) —
     *    cover SV khoa khác mượn đồ của khoa mình.
     */
    @Query("SELECT DISTINCT p FROM Penalty p " +
           "LEFT JOIN p.user u " +
           "LEFT JOIN p.booking b " +
           "LEFT JOIN b.managedByUnit u1 " +
           "LEFT JOIN b.resourceItem ri " +
           "LEFT JOIN ri.managedByUnit u2 " +
           "LEFT JOIN ri.template t " +
           "LEFT JOIN t.unit u3 " +
           "WHERE p.isDeleted = false " +
           "AND (:unitId IS NULL " +
           "     OR u.unit.id = :unitId " +
           "     OR u1.id = :unitId " +
           "     OR u2.id = :unitId " +
           "     OR u3.id = :unitId) " +
           "ORDER BY p.createdAt DESC")
    List<Penalty> findByUnitScope(@Param("unitId") String unitId);

    List<Penalty> findByUserIdAndStatusAndIsDeletedFalse(String userId, String status);

    @Query("SELECT COALESCE(SUM(p.penaltyPoint), 0) FROM Penalty p WHERE p.user.id = :userId AND p.status = 'ACTIVE' AND p.isDeleted = false")
    int sumActivePenaltyPointsByUserId(@Param("userId") String userId);

    long countByUserIdAndStatusAndIsDeletedFalse(String userId, String status);

    /**
     * Chống tạo trùng penalty tự động: chỉ tạo khi chưa có penalty cùng booking + cùng type còn ACTIVE.
     */
    boolean existsByBooking_IdAndPenaltyTypeAndStatusAndIsDeletedFalse(
            String bookingId, String penaltyType, String status);

    /**
     * Idempotent check chặt hơn: kiểm tra ĐÃ TỪNG tạo penalty cùng booking + cùng type
     * bất kể trạng thái hiện tại (ACTIVE / REVOKED). Dùng để cron task không phạt lại
     * một booking đã được ân xá (revoked) trước đó.
     */
    boolean existsByBooking_IdAndPenaltyTypeAndIsDeletedFalse(
            String bookingId, String penaltyType);
}
