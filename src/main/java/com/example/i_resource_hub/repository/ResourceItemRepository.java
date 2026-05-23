package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.ResourceItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceItemRepository
                extends JpaRepository<ResourceItem, String>, JpaSpecificationExecutor<ResourceItem> {
        Optional<ResourceItem> findBySerialNumber(String serialNumber);

        List<ResourceItem> findAllByTemplateIdAndIsDeletedFalse(String templateId);

        List<ResourceItem> findAllByIsDeletedFalse();

        long countByTemplate_IdAndIsDeletedFalse(String templateId);

        long countByTemplate_IdAndIsDeletedFalseAndStatus(String templateId, String status);

        long countByIsDeletedFalse();

        long countByIsDeletedFalseAndStatus(String status);

        // ===== Unit-scoped (admin truyền null = không filter) =====
        // 1 item "thuộc unit" nếu managedByUnit trực tiếp khớp, HOẶC
        // template của item có unit khớp (fallback) — pattern y hệt BookingService.getEffectiveUnit.

        @Query("SELECT COUNT(i) FROM ResourceItem i " +
                        "LEFT JOIN i.managedByUnit u1 " +
                        "LEFT JOIN i.template t " +
                        "LEFT JOIN t.unit u2 " +
                        "WHERE i.isDeleted = false " +
                        "AND (:unitId IS NULL OR u1.id = :unitId OR u2.id = :unitId)")
        long countByUnitScope(@Param("unitId") String unitId);

        @Query("SELECT COUNT(i) FROM ResourceItem i " +
                        "LEFT JOIN i.managedByUnit u1 " +
                        "LEFT JOIN i.template t " +
                        "LEFT JOIN t.unit u2 " +
                        "WHERE i.isDeleted = false " +
                        "AND i.status = :status " +
                        "AND (:unitId IS NULL OR u1.id = :unitId OR u2.id = :unitId)")
        long countByUnitScopeAndStatus(@Param("unitId") String unitId,
                        @Param("status") String status);

        /**
         * Đếm thiết bị "hỏng" theo nghĩa rộng: status = 'DAMAGED' HOẶC conditionStatus = 'DAMAGED'.
         * status (operational) và conditionStatus (physical) là 2 trường độc lập — một item có thể
         * conditionStatus=DAMAGED nhưng status=AVAILABLE (chưa khoá lại sau khi ghi nhận hư hỏng).
         * Dashboard "Sức khoẻ kho" phải tính cả 2 đường này để không bỏ sót.
         */
        @Query("SELECT COUNT(i) FROM ResourceItem i " +
                        "LEFT JOIN i.managedByUnit u1 " +
                        "LEFT JOIN i.template t " +
                        "LEFT JOIN t.unit u2 " +
                        "WHERE i.isDeleted = false " +
                        "AND (i.status = 'DAMAGED' OR i.conditionStatus = 'DAMAGED') " +
                        "AND (:unitId IS NULL OR u1.id = :unitId OR u2.id = :unitId)")
        long countDamagedByUnitScope(@Param("unitId") String unitId);

        @Query("SELECT DISTINCT i FROM ResourceItem i " +
                        "LEFT JOIN i.managedByUnit u1 " +
                        "LEFT JOIN i.template t " +
                        "LEFT JOIN t.unit u2 " +
                        "WHERE i.isDeleted = false " +
                        "AND (:unitId IS NULL OR u1.id = :unitId OR u2.id = :unitId)")
        List<ResourceItem> findAllActiveByUnitScope(@Param("unitId") String unitId);

        @Query("SELECT i FROM ResourceItem i WHERE i.template.id = :templateId " +
                        "AND i.isDeleted = false " +
                        "AND i.status NOT IN ('DAMAGED', 'MAINTENANCE', 'LOST') " +
                        "AND (i.conditionStatus IS NULL OR i.conditionStatus NOT IN ('DAMAGED', 'LOST')) " +
                        "AND i.id NOT IN (SELECT b.resourceItem.id FROM Booking b JOIN b.slot s " +
                        "WHERE b.bookingDate = :date " +
                        "AND b.status IN ('PENDING', 'APPROVED', 'BORROWED') " +
                        "AND s.startTime < :endTime AND s.endTime > :startTime)")
        List<ResourceItem> findAvailableItems(@Param("templateId") String templateId,
                        @Param("date") LocalDate date,
                        @Param("startTime") java.time.LocalTime startTime,
                        @Param("endTime") java.time.LocalTime endTime);

        // Cùng truy vấn nhưng khoá row để tránh race condition khi 2 user cùng đặt món cuối.
        // Phải chạy trong @Transactional, ưu tiên dùng ở createBooking().
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT i FROM ResourceItem i WHERE i.template.id = :templateId " +
                        "AND i.isDeleted = false " +
                        "AND i.status NOT IN ('DAMAGED', 'MAINTENANCE', 'LOST') " +
                        "AND (i.conditionStatus IS NULL OR i.conditionStatus NOT IN ('DAMAGED', 'LOST')) " +
                        "AND i.id NOT IN (SELECT b.resourceItem.id FROM Booking b JOIN b.slot s " +
                        "WHERE b.bookingDate = :date " +
                        "AND b.status IN ('PENDING', 'APPROVED', 'BORROWED') " +
                        "AND s.startTime < :endTime AND s.endTime > :startTime)")
        List<ResourceItem> findAvailableItemsForUpdate(@Param("templateId") String templateId,
                        @Param("date") LocalDate date,
                        @Param("startTime") java.time.LocalTime startTime,
                        @Param("endTime") java.time.LocalTime endTime);
}
