package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.response.DashboardResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Đẩy snapshot DashboardResponse mới nhất lên /topic/dashboard mỗi khi
 * Booking / ResourceItem (hoặc nguồn dữ liệu liên quan) thay đổi.
 *
 * Có debounce DEBOUNCE_MS để gom nhiều thay đổi liên tiếp (bulk create, cron…)
 * vào 1 lần build payload + push duy nhất.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardPushService {

    public static final String TOPIC = "/topic/dashboard";
    private static final long DEBOUNCE_MS = 1500;

    private final DashboardService dashboardService;
    private final SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dashboard-push");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

    /**
     * Đặt lịch push lại. Nếu đã có lịch chờ, huỷ để gom vào lần tới — tránh spam.
     * Caller có thể gọi nhiều lần trong cùng 1 transaction; chỉ 1 push thực sự xảy ra.
     */
    public void scheduleRefresh() {
        ScheduledFuture<?> existing = pending.getAndSet(null);
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> next = scheduler.schedule(this::doPush, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        pending.set(next);
    }

    private void doPush() {
        try {
            DashboardResponse payload = dashboardService.getDashboardStats();
            messagingTemplate.convertAndSend(TOPIC, payload);
            log.debug("Pushed dashboard refresh to {}", TOPIC);
        } catch (Exception e) {
            log.warn("Push dashboard refresh thất bại: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
