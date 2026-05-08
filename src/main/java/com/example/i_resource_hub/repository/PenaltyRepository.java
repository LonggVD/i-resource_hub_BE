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

    List<Penalty> findByUserIdAndStatusAndIsDeletedFalse(String userId, String status);

    @Query("SELECT COALESCE(SUM(p.penaltyPoint), 0) FROM Penalty p WHERE p.user.id = :userId AND p.status = 'ACTIVE' AND p.isDeleted = false")
    int sumActivePenaltyPointsByUserId(@Param("userId") String userId);

    long countByUserIdAndStatusAndIsDeletedFalse(String userId, String status);

    /**
     * Chống tạo trùng penalty tự động: chỉ tạo khi chưa có penalty cùng booking + cùng type còn ACTIVE.
     */
    boolean existsByBooking_IdAndPenaltyTypeAndStatusAndIsDeletedFalse(
            String bookingId, String penaltyType, String status);
}
