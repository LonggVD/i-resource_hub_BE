package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.response.DashboardResponse;
import com.example.i_resource_hub.entity.Booking;
import com.example.i_resource_hub.repository.BookingRepository;
import com.example.i_resource_hub.repository.ResourceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final BookingRepository bookingRepository;
    private final ResourceItemRepository resourceItemRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardStats() {

        // 1. Chỉ số tổng quan
        long totalEquipment = resourceItemRepository.countByIsDeletedFalse();
        long availableEquipment = resourceItemRepository.countByIsDeletedFalseAndStatus("AVAILABLE");
        long inUseEquipment = resourceItemRepository.countByIsDeletedFalseAndStatus("IN_USE");
        long brokenEquipment = resourceItemRepository.countByIsDeletedFalseAndStatus("BROKEN");
        long pendingBookings = bookingRepository.countByStatus("PENDING");

        // 2. Biểu đồ trạng thái thiết bị
        long maintenanceEquipment = resourceItemRepository.countByIsDeletedFalseAndStatus("MAINTENANCE");
        long lostEquipment = resourceItemRepository.countByIsDeletedFalseAndStatus("LOST");

        DashboardResponse.EquipmentStatusChart equipmentStatusChart = DashboardResponse.EquipmentStatusChart.builder()
                .available(availableEquipment)
                .inUse(inUseEquipment)
                .broken(brokenEquipment)
                .maintenance(maintenanceEquipment)
                .lost(lostEquipment)
                .build();

        // 3. Biểu đồ mượn theo ngày (7 ngày qua)
        LocalDate startDate = LocalDate.now().minusDays(6);
        List<Object[]> bookingsByDateRaw = bookingRepository.countBookingsByDate(startDate);
        
        List<String> labels = new ArrayList<>();
        List<Long> chartData = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");

        // Khởi tạo 7 ngày với giá trị 0
        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            labels.add(date.format(formatter));
            chartData.add(0L);
        }

        // Đổ dữ liệu thật vào
        for (Object[] row : bookingsByDateRaw) {
            if (row[0] != null) {
                // native query trả về java.sql.Date cho trường DATE
                LocalDate dbDate = ((Date) row[0]).toLocalDate();
                long count = ((Number) row[1]).longValue();
                
                int index = (int) ChronoUnit.DAYS.between(startDate, dbDate);
                if (index >= 0 && index < 7) {
                    chartData.set(index, count);
                }
            }
        }

        DashboardResponse.ChartData lineChart = DashboardResponse.ChartData.builder()
                .labels(labels)
                .data(chartData)
                .build();

        // 4. Top 5 thiết bị
        List<Object[]> topEquipRaw = bookingRepository.findTopBorrowedTemplates();
        List<DashboardResponse.TopEquipment> topEquips = topEquipRaw.stream()
                .limit(5)
                .map(row -> DashboardResponse.TopEquipment.builder()
                        .templateName((String) row[0])
                        .borrowCount(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        // 5. Mượn quá hạn
        List<Booking> candidates = bookingRepository.findOverdueCandidates(LocalDate.now());
        List<DashboardResponse.OverdueBooking> overdues = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Booking b : candidates) {
            if (b.getSlot() == null || b.getBookingDate() == null) continue;
            LocalDateTime endTime = LocalDateTime.of(b.getBookingDate(), b.getSlot().getEndTime());
            
            if (now.isAfter(endTime)) {
                long overdueDays = ChronoUnit.DAYS.between(endTime.toLocalDate(), now.toLocalDate());
                
                overdues.add(DashboardResponse.OverdueBooking.builder()
                        .bookingId(b.getId())
                        .studentCode(b.getUser() != null ? b.getUser().getStudentCode() : "N/A")
                        .studentName(b.getUser() != null ? b.getUser().getFullName() : "N/A")
                        .deviceName(b.getResourceItem() != null && b.getResourceItem().getTemplate() != null ? b.getResourceItem().getTemplate().getName() : "N/A")
                        .serialNumber(b.getResourceItem() != null ? b.getResourceItem().getSerialNumber() : "N/A")
                        .expectedReturnTime(endTime.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")))
                        .overdueDays(overdueDays)
                        .build());
            }
        }

        return DashboardResponse.builder()
                .totalEquipment(totalEquipment)
                .availableEquipment(availableEquipment)
                .inUseEquipment(inUseEquipment)
                .brokenEquipment(brokenEquipment)
                .pendingBookings(pendingBookings)
                .bookingsChart(lineChart)
                .equipmentStatusChart(equipmentStatusChart)
                .topEquipments(topEquips)
                .overdueBookings(overdues)
                .build();
    }
}
