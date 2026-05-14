package com.example.i_resource_hub.service;

import com.example.i_resource_hub.entity.*;
import com.example.i_resource_hub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test BookingService - tập trung vào:
 *   1. getAvailableQuantity (realtime check tồn kho)
 *   2. autoCancelExpiredBookings (fix mới: không reset item đang IN_USE)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("BookingService - Core Logic Tests")
class BookingServiceTest {

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private ResourceItemRepository resourceItemRepository;
    @Autowired private ResourceTemplateRepository resourceTemplateRepository;

    @MockBean private NotificationService notificationService;

    private User student;
    private TimeSlot slot;
    private ResourceTemplate template;
    private ResourceItem item;

    @BeforeEach
    void setUp() {
        // @MockBean tự return null cho mọi method không stub - đủ cho test
        student = userRepository.save(User.builder()
                .username("sv_" + UUID.randomUUID().toString().substring(0, 8))
                .password("pwd")
                .fullName("Sinh viên Test")
                .email(UUID.randomUUID() + "@test.local")
                .creditScore(100)
                .status("ACTIVE")
                .failedLoginAttempts(0)
                .build());

        slot = timeSlotRepository.save(TimeSlot.builder()
                .slotName("Ca 1")
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(9, 15))
                .build());

        template = resourceTemplateRepository.save(ResourceTemplate.builder()
                .name("Máy chiếu test")
                .description("Test")
                .isAutoApprove(false)
                .build());

        item = resourceItemRepository.save(ResourceItem.builder()
                .template(template)
                .serialNumber("SN-" + UUID.randomUUID().toString().substring(0, 8))
                .status("AVAILABLE")
                .conditionStatus("GOOD")
                .build());
    }

    @Test
    @DisplayName("getAvailableQuantity: trả về đúng số lượng AVAILABLE khi chưa có booking")
    void getAvailableQuantity_NoBooking_ReturnsAllAvailable() {
        int qty = bookingService.getAvailableQuantity(
                template.getId(), LocalDate.now().plusDays(1), slot.getId());

        assertThat(qty).isEqualTo(1); // chỉ có 1 item AVAILABLE
    }

    @Test
    @DisplayName("getAvailableQuantity: giảm khi đã có booking PENDING/APPROVED/BORROWED ở cùng slot")
    void getAvailableQuantity_WithExistingBooking_ReturnsZero() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Tạo 1 booking APPROVED chiếm chỗ
        bookingRepository.save(Booking.builder()
                .user(student)
                .resourceItem(item)
                .bookingDate(tomorrow)
                .slot(slot)
                .status("APPROVED")
                .qrCodeToken(UUID.randomUUID().toString())
                .batchToken(UUID.randomUUID().toString())
                .build());

        int qty = bookingService.getAvailableQuantity(template.getId(), tomorrow, slot.getId());

        assertThat(qty).isEqualTo(0); // chiếc duy nhất đã bị chiếm
    }

    @Test
    @DisplayName("getAvailableQuantity: trả về đầy đủ khi booking ở NGÀY KHÁC")
    void getAvailableQuantity_BookingOnDifferentDay_ReturnsAllAvailable() {
        // Booking ngày khác KHÔNG ảnh hưởng tồn ngày check
        bookingRepository.save(Booking.builder()
                .user(student)
                .resourceItem(item)
                .bookingDate(LocalDate.now().plusDays(5))
                .slot(slot)
                .status("APPROVED")
                .qrCodeToken(UUID.randomUUID().toString())
                .batchToken(UUID.randomUUID().toString())
                .build());

        int qty = bookingService.getAvailableQuantity(
                template.getId(), LocalDate.now().plusDays(1), slot.getId());

        assertThat(qty).isEqualTo(1);
    }

    @Test
    @DisplayName("CRITICAL FIX: autoCancelExpiredBookings KHÔNG reset item đang IN_USE")
    void autoCancelExpiredBookings_DoesNotResetItemInUse() {
        // Booking APPROVED hôm qua (quá hạn) — nhưng item đã handover sang đơn khác và đang IN_USE
        Booking expiredApproved = bookingRepository.save(Booking.builder()
                .user(student)
                .resourceItem(item)
                .bookingDate(LocalDate.now().minusDays(1))
                .slot(slot)
                .status("APPROVED")
                .qrCodeToken(UUID.randomUUID().toString())
                .batchToken(UUID.randomUUID().toString())
                .build());

        // Item đang IN_USE (do đơn khác đã handover)
        item.setStatus("IN_USE");
        resourceItemRepository.save(item);

        bookingService.autoCancelExpiredBookings();

        // Booking được CANCELLED
        Booking reloadedBooking = bookingRepository.findById(expiredApproved.getId()).orElseThrow();
        assertThat(reloadedBooking.getStatus()).isEqualTo("CANCELLED");

        // NHƯNG item phải GIỮ NGUYÊN trạng thái IN_USE (không bị reset sai)
        ResourceItem reloadedItem = resourceItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloadedItem.getStatus())
                .as("Item đang IN_USE không được reset thành AVAILABLE")
                .isEqualTo("IN_USE");
    }

    @Test
    @DisplayName("autoCancelExpiredBookings: ĐƯỢC reset item đang RESERVED về AVAILABLE")
    void autoCancelExpiredBookings_ResetsReservedItemToAvailable() {
        bookingRepository.save(Booking.builder()
                .user(student)
                .resourceItem(item)
                .bookingDate(LocalDate.now().minusDays(1))
                .slot(slot)
                .status("APPROVED")
                .qrCodeToken(UUID.randomUUID().toString())
                .batchToken(UUID.randomUUID().toString())
                .build());

        item.setStatus("RESERVED");
        resourceItemRepository.save(item);

        bookingService.autoCancelExpiredBookings();

        ResourceItem reloadedItem = resourceItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloadedItem.getStatus())
                .as("Item RESERVED chưa handover → được giải phóng về AVAILABLE")
                .isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("autoCancelExpiredBookings: KHÔNG đụng vào item đang MAINTENANCE / DAMAGED")
    void autoCancelExpiredBookings_PreservesMaintenanceStatus() {
        bookingRepository.save(Booking.builder()
                .user(student)
                .resourceItem(item)
                .bookingDate(LocalDate.now().minusDays(1))
                .slot(slot)
                .status("APPROVED")
                .qrCodeToken(UUID.randomUUID().toString())
                .batchToken(UUID.randomUUID().toString())
                .build());

        item.setStatus("MAINTENANCE");
        resourceItemRepository.save(item);

        bookingService.autoCancelExpiredBookings();

        ResourceItem reloadedItem = resourceItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloadedItem.getStatus()).isEqualTo("MAINTENANCE");
    }
}
