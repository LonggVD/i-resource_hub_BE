package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByUser_IdAndIsDeletedFalseOrderByCreatedAtDesc(String userId);

    Page<Notification> findByUser_IdAndIsDeletedFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUser_IdAndReadAtIsNullAndIsDeletedFalse(String userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now " +
            "WHERE n.user.id = :userId AND n.readAt IS NULL AND n.isDeleted = false")
    int markAllAsRead(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
