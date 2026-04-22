package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.ResourceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceItemRepository extends JpaRepository<ResourceItem, String>, JpaSpecificationExecutor<ResourceItem> {
    Optional<ResourceItem> findBySerialNumber(String serialNumber);
    List<ResourceItem> findAllByTemplateIdAndIsDeletedFalse(String templateId);
    List<ResourceItem> findAllByIsDeletedFalse();

    @Query("SELECT i FROM ResourceItem i WHERE i.template.id = :templateId " +
            "AND i.managedByUnit.id = :unitId " +
            "AND i.isDeleted = false " +
            "AND i.id NOT IN (SELECT b.resourceItem.id FROM Booking b " +
            "WHERE b.bookingDate = :date AND b.slot.id = :slotId " +
            "AND b.status IN ('PENDING', 'APPROVED', 'BORROWED'))")
    List<ResourceItem> findAvailableItems(@Param("templateId") String templateId,
                                          @Param("unitId") String unitId,
                                          @Param("date") LocalDate date,
                                          @Param("slotId") String slotId);
}
