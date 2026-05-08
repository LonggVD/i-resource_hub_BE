package com.example.i_resource_hub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    // 1. Chỉ số tổng quan (Summary Stats)
    private long totalEquipment;
    private long availableEquipment;
    private long inUseEquipment;
    private long brokenEquipment;
    private long pendingBookings;
    
    // 2. Biểu đồ mượn theo ngày (Line Chart)
    private ChartData bookingsChart;
    
    // 3. Biểu đồ trạng thái thiết bị (Donut Chart)
    private EquipmentStatusChart equipmentStatusChart;
    
    // 4. Top thiết bị được mượn nhiều nhất (Bar Chart)
    private List<TopEquipment> topEquipments;
    
    // 5. Danh sách sinh viên mượn quá hạn
    private List<OverdueBooking> overdueBookings;

    // --- Sub-classes ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartData {
        private List<String> labels; // VD: ["01/05", "02/05", ...]
        private List<Long> data;     // VD: [10, 25, ...]
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquipmentStatusChart {
        private long available;
        private long inUse;
        private long broken;
        private long maintenance;
        private long lost;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopEquipment {
        private String templateName;
        private long borrowCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverdueBooking {
        private String bookingId;
        private String studentCode;
        private String studentName;
        private String deviceName;
        private String serialNumber;
        private String expectedReturnTime;
        private long overdueDays;
    }
}
