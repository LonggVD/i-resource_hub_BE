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

    @Query("SELECT COUNT(b) > 0 FROM Booking b JOIN b.slot s WHERE b.resourceItem.id = :itemId " +
            "AND b.bookingDate = :date " +
            "AND b.status IN ('PENDING', 'APPROVED', 'BORROWED') " +
            "AND s.startTime < :endTime AND s.endTime > :startTime")
    boolean existsConflict(@Param("itemId") String itemId,
            @Param("date") LocalDate date,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime);

    Optional<Booking> findByQrCodeToken(String qrCodeToken);

    // Lấy danh sách Booking của riêng người dùng
    List<Booking> findByUser_IdOrderByCreatedAtDesc(String userId);

    List<Booking> findAllByStatus(String status);

    List<Booking> findAllByStatusIn(List<String> statuses);

    List<Booking> findAllByBatchToken(String batchToken);

    @Query("SELECT b FROM Booking b " +
           "LEFT JOIN b.managedByUnit u1 " +
           "LEFT JOIN b.resourceItem i " +
           "LEFT JOIN i.managedByUnit u2 " +
           "LEFT JOIN i.template t " +
           "LEFT JOIN t.unit u3 " +
           "WHERE u1.id = :unitId OR u2.id = :unitId OR u3.id = :unitId")
    List<Booking> findAllByUnitId(@Param("unitId") String unitId);

    long countByStatus(String status);

    @Query(value = "SELECT b.booking_date, COUNT(b.id) FROM bookings b WHERE b.booking_date >= :startDate GROUP BY b.booking_date ORDER BY b.booking_date", nativeQuery = true)
    List<Object[]> countBookingsByDate(@Param("startDate") LocalDate startDate);

    @Query("SELECT i.template.name, COUNT(b.id) FROM Booking b JOIN b.resourceItem i " +
           "WHERE b.status IN ('APPROVED', 'BORROWED', 'RETURNED') " +
           "GROUP BY i.template.name ORDER BY COUNT(b.id) DESC")
    List<Object[]> findTopBorrowedTemplates();

    @Query("SELECT b FROM Booking b WHERE b.status = 'BORROWED' AND b.bookingDate <= :today")
    List<Booking> findOverdueCandidates(@Param("today") LocalDate today);
}
