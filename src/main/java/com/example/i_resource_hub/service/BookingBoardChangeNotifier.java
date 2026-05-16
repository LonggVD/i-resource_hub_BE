package com.example.i_resource_hub.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phát signal "bookings đã thay đổi" lên /topic/bookings-board-changed.
 * Payload cố tình tối giản (chỉ timestamp) vì danh sách Kanban được lọc
 * theo unit của user — mỗi giảng viên phải tự gọi REST để lấy đúng phần của mình.
 *
 * Debounce DEBOUNCE_MS để 1 burst sự kiện (bulk approve, cron auto-cancel…)
 * chỉ gây ra 1 lần FE refetch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingBoardChangeNotifier {

    public static final String TOPIC = "/topic/bookings-board-changed";
    private static final long DEBOUNCE_MS = 1500;

    private final SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "board-notify");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

    public void scheduleNotify() {
        ScheduledFuture<?> existing = pending.getAndSet(null);
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> next = scheduler.schedule(this::doNotify, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        pending.set(next);
    }

    private void doNotify() {
        try {
            messagingTemplate.convertAndSend(TOPIC, Map.of("at", Instant.now().toString()));
            log.debug("Notified board change to {}", TOPIC);
        } catch (Exception e) {
            log.warn("Notify board change thất bại: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
