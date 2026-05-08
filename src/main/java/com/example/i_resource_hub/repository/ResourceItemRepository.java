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

        @Query("SELECT i FROM ResourceItem i WHERE i.template.id = :templateId " +
                        "AND i.isDeleted = false " +
                        "AND i.status NOT IN ('BROKEN', 'MAINTENANCE', 'LOST') " +
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
                        "AND i.status NOT IN ('BROKEN', 'MAINTENANCE', 'LOST') " +
                        "AND i.id NOT IN (SELECT b.resourceItem.id FROM Booking b JOIN b.slot s " +
                        "WHERE b.bookingDate = :date " +
                        "AND b.status IN ('PENDING', 'APPROVED', 'BORROWED') " +
                        "AND s.startTime < :endTime AND s.endTime > :startTime)")
        List<ResourceItem> findAvailableItemsForUpdate(@Param("templateId") String templateId,
                        @Param("date") LocalDate date,
                        @Param("startTime") java.time.LocalTime startTime,
                        @Param("endTime") java.time.LocalTime endTime);
}
