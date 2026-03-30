package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.ResourceTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceTemplateRepository extends JpaRepository<ResourceTemplate, String> {

    @EntityGraph(attributePaths = {"category", "unit"})
    List<ResourceTemplate> findAllByIsDeletedFalse();

    @EntityGraph(attributePaths = {"category", "unit"})
    List<ResourceTemplate> findAllByIsDeletedTrue();

    @EntityGraph(attributePaths = {"category", "unit"})
    Optional<ResourceTemplate> findByIdAndIsDeletedFalse(String id);

    Optional<ResourceTemplate> findByIdAndIsDeletedTrue(String id);

    //xo cứng khỏi db
    @Query(value = """
            DELETE FROM resource_templates
            WHERE is_deleted = true
            AND updated_at < :thresholdate
        """, nativeQuery = true)
    void deleteRTOldRecords(@Param("thresholdate") LocalDateTime thresholdate);

}

