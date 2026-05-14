package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử các native query atomic UPDATE trong UserRepository
 * — đảm bảo creditScore được trừ/cộng đúng và auto-lock/unlock account hoạt động.
 *
 * Đây là test "lá chắn" cho fix race condition đã làm ở PenaltyService.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class UserRepositoryAtomicTest {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("test_user_" + System.nanoTime())
                .password("$2a$10$dummy.hash.for.testing.purposes.only")
                .fullName("Sinh viên Kiểm thử")
                .email("test_" + System.nanoTime() + "@example.com")
                .creditScore(100)
                .status("ACTIVE")
                .failedLoginAttempts(0)
                .build();
        testUser = userRepository.saveAndFlush(testUser);
    }

    @Test
    @DisplayName("deductCreditScore trừ đúng điểm khi score đủ lớn")
    void deductCreditScore_normalCase_subtractsCorrectly() {
        userRepository.deductCreditScore(testUser.getId(), 30);

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore()).isEqualTo(70);
        assertThat(reloaded.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("deductCreditScore: nếu trừ vượt 0 thì score = 0 và status tự động LOCKED")
    void deductCreditScore_exceedsMin_clampsToZeroAndLocks() {
        userRepository.deductCreditScore(testUser.getId(), 150);

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore())
                .as("creditScore không được âm — phải clamp tại 0")
                .isEqualTo(0);
        assertThat(reloaded.getStatus())
                .as("Auto-lock account khi score chạm 0")
                .isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("deductCreditScore: trừ chính xác đến 0 cũng phải lock")
    void deductCreditScore_exactlyZero_locks() {
        userRepository.deductCreditScore(testUser.getId(), 100);

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore()).isEqualTo(0);
        assertThat(reloaded.getStatus()).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("restoreCreditScore hoàn điểm và unlock account khi đang LOCKED")
    void restoreCreditScore_unlocksWhenLockedAndScorePositive() {
        // Đưa user vào trạng thái bị LOCKED + score 0
        userRepository.deductCreditScore(testUser.getId(), 100);

        // Ân xá: hoàn 30 điểm
        userRepository.restoreCreditScore(testUser.getId(), 30);

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore()).isEqualTo(30);
        assertThat(reloaded.getStatus())
                .as("Status phải tự động chuyển ACTIVE khi score > 0")
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("restoreCreditScore không vượt 100 (giới hạn LEAST)")
    void restoreCreditScore_neverExceeds100() {
        // User mới có 100 điểm, cộng thêm 30 — phải clamp tại 100
        userRepository.restoreCreditScore(testUser.getId(), 30);

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore())
                .as("creditScore tối đa là 100, không vượt được")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("Tích hợp: 3 lần trừ liên tiếp đều atomic và đúng tổng")
    void deductCreditScore_multipleConsecutive_sumsCorrectly() {
        userRepository.deductCreditScore(testUser.getId(), 10);
        userRepository.deductCreditScore(testUser.getId(), 20);
        userRepository.deductCreditScore(testUser.getId(), 30);

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getCreditScore())
                .as("Tổng trừ 60, score còn lại = 40")
                .isEqualTo(40);
        assertThat(reloaded.getStatus()).isEqualTo("ACTIVE");
    }
}
