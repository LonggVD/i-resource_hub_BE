package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, String> {

    /** Lấy tất cả ca mượn chưa bị xóa mềm */
    List<TimeSlot> findByIsDeletedFalse();

    /** Tìm tất cả ca theo ngày trong tuần (1–7) chưa bị xóa. */
    List<TimeSlot> findByDayOfWeekAndIsDeletedFalse(Integer dayOfWeek);

    /** Tìm các ca áp dụng mọi ngày (dayOfWeek = null). */
    /** Tìm các ca áp dụng mọi ngày (dayOfWeek = null) chưa bị xóa. */
    List<TimeSlot> findByDayOfWeekIsNullAndIsDeletedFalse();

    /** Kiểm tra tên ca đã tồn tại chưa (để tránh duplicate). */
    /** Kiểm tra tên ca đã tồn tại và đang active chưa (để tránh duplicate). */
    boolean existsBySlotNameAndIsDeletedFalse(String slotName);

    /**
     * Tìm các khung giờ bị trùng lặp (overlap) trên cùng dayOfWeek.
     * Hai ca bị trùng khi: startA < endB AND startB < endA
     * Ngoại trừ chính nó (excludeId) khi đang update.
     */
    @Query("SELECT t FROM TimeSlot t WHERE t.isDeleted = false " +
           "AND t.id <> :excludeId " +
           "AND t.startTime < :endTime AND t.endTime > :startTime " +
           "AND (t.dayOfWeek = :dayOfWeek OR t.dayOfWeek IS NULL OR :dayOfWeek IS NULL)")
    List<TimeSlot> findOverlapping(
            @Param("excludeId") String excludeId,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("dayOfWeek") Integer dayOfWeek);
}
