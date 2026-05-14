package com.example.i_resource_hub.service;

import com.example.i_resource_hub.entity.Booking;
import com.example.i_resource_hub.entity.Penalty;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.BookingRepository;
import com.example.i_resource_hub.repository.PenaltyRepository;
import com.example.i_resource_hub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test PenaltyService - tập trung vào 2 fix mới:
 *   1. Idempotent check cover REVOKED status (không phạt lại sau khi ân xá)
 *   2. Atomic UPDATE creditScore (deduct/restore)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("PenaltyService - Logic Tests")
class PenaltyServiceTest {

    @Autowired
    private PenaltyService penaltyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PenaltyRepository penaltyRepository;

    @Autowired
    private BookingRepository bookingRepository;

    // Mock notification để tránh gọi WebSocket trong test
    @MockBean
    private NotificationService notificationService;

    private User testStudent;
    private Booking testBooking;

    @BeforeEach
    void setUp() {
        // @MockBean tự return null cho mọi method không stub - đủ cho test (createAndPush không ảnh hưởng logic)
        testStudent = User.builder()
                .username("student_" + UUID.randomUUID().toString().substring(0, 8))
                .password("pwd")
                .fullName("Sinh viên Test")
                .email(UUID.randomUUID() + "@test.local")
                .creditScore(100)
                .status("ACTIVE")
                .failedLoginAttempts(0)
                .build();
        testStudent = userRepository.save(testStudent);

        testBooking = Booking.builder()
                .user(testStudent)
                .bookingDate(LocalDate.now().minusDays(1)) // hôm qua
                .status("BORROWED")
                .qrCodeToken(UUID.randomUUID().toString())
                .batchToken(UUID.randomUUID().toString())
                .build();
        testBooking = bookingRepository.save(testBooking);
    }

    @Test
    @DisplayName("createSystemPenalty: tạo penalty thành công + trừ creditScore qua atomic UPDATE")
    void createSystemPenalty_HappyPath() {
        Penalty penalty = penaltyService.createSystemPenalty(
                testStudent, testBooking, "OVERDUE", 10, "Trễ trả 2 giờ");

        assertThat(penalty).isNotNull();
        assertThat(penalty.getStatus()).isEqualTo("ACTIVE");
        assertThat(penalty.getPenaltyType()).isEqualTo("OVERDUE");
        assertThat(penalty.getCreatedByUser()).isNull(); // do hệ thống tạo

        User reloaded = userRepository.findById(testStudent.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore()).isEqualTo(90);
        assertThat(reloaded.getStatus()).isEqualTo("ACTIVE"); // chưa tới 0
    }

    @Test
    @DisplayName("createSystemPenalty: tự lock account khi score về 0")
    void createSystemPenalty_LockAccountWhenScoreReachesZero() {
        penaltyService.createSystemPenalty(
                testStudent, testBooking, "LOST", 100, "Mất thiết bị");

        User reloaded = userRepository.findById(testStudent.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore()).isEqualTo(0);
        assertThat(reloaded.getStatus()).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("Idempotent: gọi createSystemPenalty 2 lần cho cùng booking+type → chỉ tạo 1")
    void createSystemPenalty_Idempotent_SameBookingAndType() {
        Penalty first = penaltyService.createSystemPenalty(
                testStudent, testBooking, "OVERDUE", 10, "Lần 1");
        Penalty second = penaltyService.createSystemPenalty(
                testStudent, testBooking, "OVERDUE", 10, "Lần 2");

        assertThat(first).isNotNull();
        assertThat(second).as("Lần 2 phải bị skip vì đã có penalty ACTIVE cho booking này").isNull();

        // CreditScore chỉ bị trừ 1 lần
        User reloaded = userRepository.findById(testStudent.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore()).isEqualTo(90);
    }

    @Test
    @DisplayName("CRITICAL FIX: Idempotent cover REVOKED - đã ân xá rồi cron không phạt lại")
    void createSystemPenalty_Idempotent_CoversRevokedStatus() {
        // 1. Cron tạo penalty
        Penalty p = penaltyService.createSystemPenalty(
                testStudent, testBooking, "OVERDUE", 10, "Trễ trả lần 1");
        assertThat(p).isNotNull();

        // 2. Admin ân xá
        penaltyService.revokePenalty(p.getId());
        Penalty revoked = penaltyRepository.findById(p.getId()).orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo("REVOKED");

        // 3. Cron chạy lại — KHÔNG được tạo penalty mới cho cùng booking + cùng type
        Penalty secondAttempt = penaltyService.createSystemPenalty(
                testStudent, testBooking, "OVERDUE", 10, "Cron chạy lại");
        assertThat(secondAttempt)
                .as("Đã có penalty REVOKED cho booking này → KHÔNG được tạo lại")
                .isNull();

        // 4. CreditScore: ban đầu 100, trừ 10 (lần 1) → 90, ân xá +10 → 100. Không bị tạo lại lần 2.
        User reloaded = userRepository.findById(testStudent.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("revokePenalty: hoàn điểm + unlock account đang LOCKED")
    void revokePenalty_RestoresScoreAndUnlocks() {
        // Phạt nặng để account bị LOCKED
        Penalty p = penaltyService.createSystemPenalty(
                testStudent, testBooking, "LOST", 100, "Mất thiết bị");
        assertThat(userRepository.findById(testStudent.getId()).orElseThrow().getStatus())
                .isEqualTo("LOCKED");

        // Ân xá
        penaltyService.revokePenalty(p.getId());

        User reloaded = userRepository.findById(testStudent.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore()).isEqualTo(100); // 0 + 100 = 100 (cap)
        assertThat(reloaded.getStatus()).isEqualTo("ACTIVE"); // unlock
    }

    @Test
    @DisplayName("revokePenalty: không cộng vượt 100 điểm")
    void revokePenalty_DoesNotExceedMax100() {
        // User đang 100, tạo penalty 5 điểm → còn 95
        Penalty p = penaltyService.createSystemPenalty(
                testStudent, testBooking, "OVERDUE", 5, "Trễ trả nhẹ");
        assertThat(userRepository.findById(testStudent.getId()).orElseThrow().getCreditScore())
                .isEqualTo(95);

        // Ân xá → cộng 5 → đúng 100, không vượt
        penaltyService.revokePenalty(p.getId());
        assertThat(userRepository.findById(testStudent.getId()).orElseThrow().getCreditScore())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("Skip nếu user null hoặc penaltyType blank")
    void createSystemPenalty_NullUserOrBlankType_ReturnsNull() {
        assertThat(penaltyService.createSystemPenalty(null, testBooking, "OVERDUE", 10, "x"))
                .isNull();
        assertThat(penaltyService.createSystemPenalty(testStudent, testBooking, "", 10, "x"))
                .isNull();
        assertThat(penaltyService.createSystemPenalty(testStudent, testBooking, null, 10, "x"))
                .isNull();
    }
}
