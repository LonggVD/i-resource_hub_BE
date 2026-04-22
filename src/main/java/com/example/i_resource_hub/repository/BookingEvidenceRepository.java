package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.BookingEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingEvidenceRepository extends JpaRepository<BookingEvidence, String> {
}
