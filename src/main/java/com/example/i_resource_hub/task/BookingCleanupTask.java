package com.example.i_resource_hub.task;

import com.example.i_resource_hub.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCleanupTask {

    private final BookingService bookingService;

    /**
     * Mỗi 5 phút:
     *   1. Tự huỷ đơn PENDING/APPROVED quá hạn ca (sinh viên không đến nhận / giáo vụ chưa duyệt kịp)
     *   2. Tự sinh penalty LATE_RETURN cho đơn BORROWED quá hạn trả + grace minutes
     */
    @Scheduled(fixedRate = 300000) // 5 phút
    public void runScheduledCleanup() {
        try {
            bookingService.autoCancelExpiredBookings();
        } catch (Exception ex) {
            log.error("autoCancelExpiredBookings thất bại", ex);
        }

        try {
            bookingService.autoPenalizeOverdueReturns();
        } catch (Exception ex) {
            log.error("autoPenalizeOverdueReturns thất bại", ex);
        }
    }
}
