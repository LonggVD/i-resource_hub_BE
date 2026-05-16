package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.OverdueRemindRequest;
import com.example.i_resource_hub.dto.response.DashboardResponse;
import com.example.i_resource_hub.dto.response.OverdueRemindResponse;
import com.example.i_resource_hub.entity.Booking;
import com.example.i_resource_hub.repository.BookingRepository;
import com.example.i_resource_hub.repository.ResourceItemRepository;
import com.example.i_resource_hub.security.AuthorizationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private final BookingRepository bookingRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final EmailService emailService;
    private final AuthorizationHelper authHelper;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardStats() {

        // Admin: unitId = null = không filter (xem toàn hệ thống).
        // Manager/giáo vụ: chỉ xem trong unit của mình.
        String unitId = authHelper.getScopedUnitIdOrNull();

        // 1. Chỉ số tổng quan
        long totalEquipment = resourceItemRepository.countByUnitScope(unitId);
        long availableEquipment = resourceItemRepository.countByUnitScopeAndStatus(unitId, "AVAILABLE");
        long inUseEquipment = resourceItemRepository.countByUnitScopeAndStatus(unitId, "IN_USE");
        long brokenEquipment = resourceItemRepository.countByUnitScopeAndStatus(unitId, "BROKEN");
        long pendingBookings = bookingRepository.countByStatusAndUnitScope("PENDING", unitId);

        // 2. Biểu đồ trạng thái thiết bị
        long maintenanceEquipment = resourceItemRepository.countByUnitScopeAndStatus(unitId, "MAINTENANCE");
        long lostEquipment = resourceItemRepository.countByUnitScopeAndStatus(unitId, "LOST");

        DashboardResponse.EquipmentStatusChart equipmentStatusChart = DashboardResponse.EquipmentStatusChart.builder()
                .available(availableEquipment)
                .inUse(inUseEquipment)
                .broken(brokenEquipment)
                .maintenance(maintenanceEquipment)
                .lost(lostEquipment)
                .build();

        // 3. Biểu đồ mượn theo ngày (7 ngày qua)
        LocalDate startDate = LocalDate.now().minusDays(6);
        List<Object[]> bookingsByDateRaw = bookingRepository.countBookingsByDateAndUnitScope(startDate, unitId);
        
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
        List<Object[]> topEquipRaw = bookingRepository.findTopBorrowedTemplatesByUnitScope(unitId);
        List<DashboardResponse.TopEquipment> topEquips = topEquipRaw.stream()
                .limit(5)
                .map(row -> DashboardResponse.TopEquipment.builder()
                        .templateName((String) row[0])
                        .borrowCount(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        // 5. Mượn quá hạn
        List<Booking> candidates = bookingRepository.findOverdueCandidatesByUnitScope(LocalDate.now(), unitId);
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
                        .slotName(b.getSlot().getSlotName())
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

    /**
     * Gửi email nhắc nhở cho các booking quá hạn.
     * Xử lý từng booking riêng để 1 email lỗi không phá batch.
     */
    @Transactional(readOnly = true)
    public OverdueRemindResponse remindOverdue(OverdueRemindRequest request) {
        List<String> ids = request.getBookingIds();
        List<Booking> bookings = bookingRepository.findAllById(ids);

        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        int sent = 0;

        // Các id client gửi mà DB không tìm thấy → skip ngay (làm trước filter unit
        // để không bị nhầm vào "ngoài đơn vị").
        List<String> foundIds = bookings.stream().map(Booking::getId).collect(Collectors.toList());
        for (String id : ids) {
            if (!foundIds.contains(id)) skipped.add(id);
        }

        // Filter ngoài-unit: manager chỉ gửi nhắc cho SV trong unit mình.
        // Admin (unitId null) thì giữ nguyên.
        String scopedUnitId = authHelper.getScopedUnitIdOrNull();
        if (scopedUnitId != null) {
            List<Booking> inScope = new ArrayList<>();
            for (Booking b : bookings) {
                if (belongsToUnit(b, scopedUnitId)) {
                    inScope.add(b);
                } else {
                    skipped.add(b.getId());
                }
            }
            bookings = inScope;
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

        for (Booking b : bookings) {
            if (b.getUser() == null || b.getUser().getEmail() == null || b.getUser().getEmail().isBlank()) {
                skipped.add(b.getId());
                continue;
            }
            if (b.getSlot() == null || b.getBookingDate() == null) {
                skipped.add(b.getId());
                continue;
            }
            LocalDateTime endTime = LocalDateTime.of(b.getBookingDate(), b.getSlot().getEndTime());
            if (!now.isAfter(endTime)) {
                // Không thực sự quá hạn — bỏ qua để tránh gửi nhầm
                skipped.add(b.getId());
                continue;
            }

            long overdueDays = ChronoUnit.DAYS.between(endTime.toLocalDate(), now.toLocalDate());
            String deviceName = (b.getResourceItem() != null && b.getResourceItem().getTemplate() != null)
                    ? b.getResourceItem().getTemplate().getName() : "N/A";
            String serial = (b.getResourceItem() != null) ? b.getResourceItem().getSerialNumber() : "N/A";

            try {
                emailService.sendOverdueReminderEmail(
                        b.getUser().getEmail(),
                        b.getUser().getFullName(),
                        deviceName,
                        serial,
                        b.getSlot().getSlotName(),
                        endTime.format(fmt),
                        overdueDays
                );
                sent++;
            } catch (Exception ex) {
                log.warn("Gửi email nhắc nhở thất bại cho booking {}: {}", b.getId(), ex.getMessage());
                failed.add(b.getId());
            }
        }

        return OverdueRemindResponse.builder()
                .requested(ids.size())
                .sent(sent)
                .skippedBookingIds(skipped)
                .failedBookingIds(failed)
                .build();
    }

    /** Cùng pattern với BookingRepository#findAllByUnitId: match nếu unitId khớp 1 trong 3 đường (managedByUnit, item.managedByUnit, item.template.unit). */
    private boolean belongsToUnit(Booking b, String unitId) {
        if (b.getManagedByUnit() != null && unitId.equals(b.getManagedByUnit().getId())) return true;
        if (b.getResourceItem() != null) {
            if (b.getResourceItem().getManagedByUnit() != null
                    && unitId.equals(b.getResourceItem().getManagedByUnit().getId())) return true;
            if (b.getResourceItem().getTemplate() != null
                    && b.getResourceItem().getTemplate().getUnit() != null
                    && unitId.equals(b.getResourceItem().getTemplate().getUnit().getId())) return true;
        }
        return false;
    }
}
