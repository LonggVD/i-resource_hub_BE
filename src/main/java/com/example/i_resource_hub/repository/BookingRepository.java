package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String> {

    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE b.resourceItem.id = :itemId " +
            "AND b.bookingDate = :date AND b.slot.id = :slotId " +
            "AND b.status IN ('PENDING', 'APPROVED', 'BORROWED')")
    boolean existsConflict(@Param("itemId") String itemId,
            @Param("date") LocalDate date,
            @Param("slotId") String slotId);

    Optional<Booking> findByQrCodeToken(String qrCodeToken);

    // Lấy danh sách Booking của riêng người dùng
    List<Booking> findByUser_IdOrderByCreatedAtDesc(String userId);

    // Lấy danh sách Booking thuộc Đơn vị quản lý thiết bị chỉ định
    List<Booking> findByResourceItem_ManagedByUnit_Id(String unitId);
}
