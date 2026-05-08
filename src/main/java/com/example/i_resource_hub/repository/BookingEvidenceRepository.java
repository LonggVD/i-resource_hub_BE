package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.BookingEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface BookingEvidenceRepository extends JpaRepository<BookingEvidence, String> {
    List<BookingEvidence> findByBookingIdAndIsDeletedFalseOrderByCreatedAtDesc(String bookingId);
    
    List<BookingEvidence> findByResourceItemIdAndIsDeletedFalseOrderByCreatedAtDesc(String resourceItemId);

    @Query("SELECT e FROM BookingEvidence e WHERE e.booking.batchToken = :batchToken AND e.isDeleted = false ORDER BY e.createdAt DESC")
    List<BookingEvidence> findAllByBookingBatchToken(@Param("batchToken") String batchToken);
    
    @Query("SELECT e FROM BookingEvidence e WHERE e.booking.id IN :bookingIds AND e.isDeleted = false ORDER BY e.createdAt DESC")
    List<BookingEvidence> findByBookingIds(@Param("bookingIds") List<String> bookingIds);
}
