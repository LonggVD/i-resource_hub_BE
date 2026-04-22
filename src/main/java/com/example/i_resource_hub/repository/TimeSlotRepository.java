package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, String> {

    /** Tìm tất cả ca theo ngày trong tuần (1–7). */
    List<TimeSlot> findByDayOfWeek(Integer dayOfWeek);

    /** Tìm các ca áp dụng mọi ngày (dayOfWeek = null). */
    List<TimeSlot> findByDayOfWeekIsNull();

    /** Kiểm tra tên ca đã tồn tại chưa (để tránh duplicate). */
    boolean existsBySlotName(String slotName);
}

