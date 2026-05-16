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

    @Query("SELECT DISTINCT b FROM Booking b " +
           "LEFT JOIN FETCH b.user " +
           "LEFT JOIN FETCH b.slot " +
           "LEFT JOIN FETCH b.resourceItem ri " +
           "LEFT JOIN FETCH ri.template " +
           "LEFT JOIN FETCH b.managedByUnit " +
           "WHERE b.bookingDate BETWEEN :from AND :to " +
           "ORDER BY b.bookingDate DESC, b.createdAt DESC")
    List<Booking> findByBookingDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ===== Unit-scoped variants (admin = unitId null) =====

    /** Pattern khớp với {@link #findAllByUnitId}: 1 booking thuộc unit nếu managedByUnit / item.managedByUnit / item.template.unit khớp. */
    @Query("SELECT COUNT(b) FROM Booking b " +
           "LEFT JOIN b.managedByUnit u1 " +
           "LEFT JOIN b.resourceItem i " +
           "LEFT JOIN i.managedByUnit u2 " +
           "LEFT JOIN i.template t " +
           "LEFT JOIN t.unit u3 " +
           "WHERE b.status = :status " +
           "AND (:unitId IS NULL OR u1.id = :unitId OR u2.id = :unitId OR u3.id = :unitId)")
    long countByStatusAndUnitScope(@Param("status") String status,
                                   @Param("unitId") String unitId);

    @Query(value = "SELECT b.booking_date, COUNT(b.id) FROM bookings b " +
           "LEFT JOIN resource_items i ON i.id = b.resource_item_id " +
           "LEFT JOIN resource_templates t ON t.id = i.template_id " +
           "WHERE b.booking_date >= :startDate " +
           "AND (:unitId IS NULL OR b.managed_by_unit = :unitId OR i.unit_id = :unitId OR t.unit_id = :unitId) " +
           "GROUP BY b.booking_date ORDER BY b.booking_date", nativeQuery = true)
    List<Object[]> countBookingsByDateAndUnitScope(@Param("startDate") LocalDate startDate,
                                                   @Param("unitId") String unitId);

    @Query("SELECT i.template.name, COUNT(b.id) FROM Booking b " +
           "JOIN b.resourceItem i " +
           "LEFT JOIN b.managedByUnit u1 " +
           "LEFT JOIN i.managedByUnit u2 " +
           "LEFT JOIN i.template t " +
           "LEFT JOIN t.unit u3 " +
           "WHERE b.status IN ('APPROVED', 'BORROWED', 'RETURNED') " +
           "AND (:unitId IS NULL OR u1.id = :unitId OR u2.id = :unitId OR u3.id = :unitId) " +
           "GROUP BY i.template.name ORDER BY COUNT(b.id) DESC")
    List<Object[]> findTopBorrowedTemplatesByUnitScope(@Param("unitId") String unitId);

    @Query("SELECT b FROM Booking b " +
           "LEFT JOIN b.managedByUnit u1 " +
           "LEFT JOIN b.resourceItem i " +
           "LEFT JOIN i.managedByUnit u2 " +
           "LEFT JOIN i.template t " +
           "LEFT JOIN t.unit u3 " +
           "WHERE b.status = 'BORROWED' AND b.bookingDate <= :today " +
           "AND (:unitId IS NULL OR u1.id = :unitId OR u2.id = :unitId OR u3.id = :unitId)")
    List<Booking> findOverdueCandidatesByUnitScope(@Param("today") LocalDate today,
                                                   @Param("unitId") String unitId);

    @Query("SELECT DISTINCT b FROM Booking b " +
           "LEFT JOIN FETCH b.user " +
           "LEFT JOIN FETCH b.slot " +
           "LEFT JOIN FETCH b.resourceItem ri " +
           "LEFT JOIN FETCH ri.template tpl " +
           "LEFT JOIN FETCH b.managedByUnit u1 " +
           "LEFT JOIN ri.managedByUnit u2 " +
           "LEFT JOIN tpl.unit u3 " +
           "WHERE b.bookingDate BETWEEN :from AND :to " +
           "AND (:unitId IS NULL OR u1.id = :unitId OR u2.id = :unitId OR u3.id = :unitId) " +
           "ORDER BY b.bookingDate DESC, b.createdAt DESC")
    List<Booking> findByBookingDateBetweenAndUnitScope(@Param("from") LocalDate from,
                                                       @Param("to") LocalDate to,
                                                       @Param("unitId") String unitId);
}
