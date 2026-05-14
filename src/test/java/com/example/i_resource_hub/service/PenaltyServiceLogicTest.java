package com.example.i_resource_hub.service;

import com.example.i_resource_hub.entity.Penalty;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Test PenaltyService — đảm bảo logic trừ điểm + lock + ân xá hoạt động đúng
 * sau khi áp dụng atomic UPDATE chống race condition.
 *
 * Để tránh phải dựng nguyên Booking + Slot + ResourceItem cho test, ta dùng
 * createSystemPenalty với booking=null (system penalty không bắt buộc booking).
 *
 * NotificationService bị mock để không gửi WebSocket thật trong test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PenaltyServiceLogicTest {

    @Autowired
    private PenaltyService penaltyService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private NotificationService notificationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("penalty_test_" + System.nanoTime())
                .password("$2a$10$dummy.hash")
                .fullName("SV Kiểm thử Penalty")
                .email("penalty_" + System.nanoTime() + "@test.com")
                .creditScore(100)
                .status("ACTIVE")
                .failedLoginAttempts(0)
                .build();
        testUser = userRepository.saveAndFlush(testUser);
    }

    @Test
    @DisplayName("createSystemPenalty trừ đúng creditScore qua atomic UPDATE")
    void createSystemPenalty_deductsCreditScore() {
        Penalty penalty = penaltyService.createSystemPenalty(
                testUser, null, "GENERIC", 30, "Test deduct creditScore");

        assertThat(penalty).isNotNull();
        assertThat(penalty.getStatus()).isEqualTo("ACTIVE");

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore())
                .as("Score 100 - 30 = 70")
                .isEqualTo(70);
        assertThat(reloaded.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("createSystemPenalty trừ vượt 0 → score=0 và account auto-LOCKED")
    void createSystemPenalty_overdraft_locksAccount() {
        Penalty penalty = penaltyService.createSystemPenalty(
                testUser, null, "DAMAGE", 150, "Phạt nặng vượt mức điểm");

        assertThat(penalty).isNotNull();

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore())
                .as("Score không được âm — clamp tại 0")
                .isEqualTo(0);
        assertThat(reloaded.getStatus())
                .as("Auto-lock khi score chạm 0")
                .isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("createSystemPenalty: 3 lần trừ liên tiếp đều atomic + tổng đúng")
    void createSystemPenalty_multipleDeducts_sumCorrectly() {
        penaltyService.createSystemPenalty(testUser, null, "P1", 10, "Lần 1");
        penaltyService.createSystemPenalty(testUser, null, "P2", 20, "Lần 2");
        penaltyService.createSystemPenalty(testUser, null, "P3", 30, "Lần 3");

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore())
                .as("Tổng trừ 60, còn lại 40")
                .isEqualTo(40);
    }

    @Test
    @DisplayName("createSystemPenalty với user=null hoặc penaltyType=null → trả về null không lỗi")
    void createSystemPenalty_invalidInput_returnsNull() {
        Penalty p1 = penaltyService.createSystemPenalty(null, null, "ANY", 10, "test");
        Penalty p2 = penaltyService.createSystemPenalty(testUser, null, null, 10, "test");
        Penalty p3 = penaltyService.createSystemPenalty(testUser, null, "  ", 10, "test");

        assertThat(p1).isNull();
        assertThat(p2).isNull();
        assertThat(p3).isNull();

        // Score không thay đổi
        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("createSystemPenalty gửi notification cho người vi phạm")
    void createSystemPenalty_sendsNotification() {
        penaltyService.createSystemPenalty(
                testUser, null, "OVERDUE", 10, "Trễ trả thiết bị");

        // Verify NotificationService.createAndPush được gọi với type PENALTY_CREATED
        verify(notificationService, atLeastOnce())
                .createAndPush(eq(testUser), eq("PENALTY_CREATED"), any(), any(), any());
    }

    @Test
    @DisplayName("revokePenalty hoàn điểm + auto-unlock account nếu đang LOCKED")
    void revokePenalty_unlocksAccount() {
        // 1. Tạo penalty đẩy user → LOCKED
        Penalty penalty = penaltyService.createSystemPenalty(
                testUser, null, "OVERDUE", 100, "Phạt đến 0 → lock");

        User locked = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(locked.getStatus()).isEqualTo("LOCKED");
        assertThat(locked.getCreditScore()).isEqualTo(0);

        // 2. Ân xá
        penaltyService.revokePenalty(penalty.getId());

        // 3. Score hoàn lại + status unlocked
        User unlocked = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(unlocked.getCreditScore())
                .as("Hoàn 100 điểm, clamp tại 100")
                .isEqualTo(100);
        assertThat(unlocked.getStatus())
                .as("Auto-unlock khi score > 0")
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("revokePenalty 2 lần liên tiếp trên cùng penalty → lần 2 throw exception")
    void revokePenalty_twice_throwsOnSecond() {
        Penalty penalty = penaltyService.createSystemPenalty(
                testUser, null, "OVERDUE", 20, "test");

        penaltyService.revokePenalty(penalty.getId());

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> penaltyService.revokePenalty(penalty.getId()));
    }
}
