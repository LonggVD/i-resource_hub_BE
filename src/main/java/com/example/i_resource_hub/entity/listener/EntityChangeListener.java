package com.example.i_resource_hub.entity.listener;

import com.example.i_resource_hub.config.BeanProvider;
import com.example.i_resource_hub.service.BookingBoardChangeNotifier;
import com.example.i_resource_hub.service.DashboardPushService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Listener gắn vào các entity có ảnh hưởng tới màn real-time (Booking, ResourceItem...).
 * Sau khi tx commit, fan-out tới các push service:
 *  - {@link DashboardPushService}: gửi snapshot dashboard.
 *  - {@link BookingBoardChangeNotifier}: gửi signal "bookings changed", FE tự refetch.
 *
 * Dedupe trong cùng 1 tx: nhiều entity change => 1 lần schedule per push service.
 * Debounce trong từng push service: nhiều tx gần nhau => 1 lần push thực sự.
 */
public class EntityChangeListener {

    private static final String SYNC_BOUND_KEY = "EntityChangeListener.bound";

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(Object entity) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            if (TransactionSynchronizationManager.getResource(SYNC_BOUND_KEY) == null) {
                TransactionSynchronizationManager.bindResource(SYNC_BOUND_KEY, Boolean.TRUE);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        TransactionSynchronizationManager.unbindResourceIfPossible(SYNC_BOUND_KEY);
                        if (status == STATUS_COMMITTED) {
                            triggerPush();
                        }
                    }
                });
            }
        } else {
            // Save ngoài transaction (hiếm) — push luôn
            triggerPush();
        }
    }

    private void triggerPush() {
        DashboardPushService dash = BeanProvider.getBean(DashboardPushService.class);
        if (dash != null) dash.scheduleRefresh();

        BookingBoardChangeNotifier board = BeanProvider.getBean(BookingBoardChangeNotifier.class);
        if (board != null) board.scheduleNotify();
    }
}
