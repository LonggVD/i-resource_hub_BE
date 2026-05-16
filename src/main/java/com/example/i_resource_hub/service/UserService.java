package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.CreditScoreRequest;
import com.example.i_resource_hub.dto.request.UserRequest;
import com.example.i_resource_hub.dto.response.OrganizationUnitResponse;
import com.example.i_resource_hub.dto.response.RoleResponse;
import com.example.i_resource_hub.dto.response.UserResponse;
import com.example.i_resource_hub.entity.OrganizationUnit;
import com.example.i_resource_hub.entity.Role;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.OrganizationUnitRepository;
import com.example.i_resource_hub.repository.RoleRepository;
import com.example.i_resource_hub.repository.UserRepository;
import com.example.i_resource_hub.repository.specification.UserSpecification;
import com.example.i_resource_hub.security.AuthorizationHelper;
import com.example.i_resource_hub.security.CustomUserDetails;
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationUnitRepository unitRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthorizationHelper authHelper;

    @Transactional(readOnly = true)
    public Page<UserResponse> getPageUsers(String keyword, String unitId, String status, String roleId, Pageable pageable) {
        // Manager: FORCE unit của mình, bỏ qua param client gửi (không cho mượn unitId của khoa khác).
        // Admin: tôn trọng param client gửi (có thể null = tất cả).
        String effectiveUnitId = authHelper.isAdmin() ? unitId : authHelper.getCurrentUnitId();
        Specification<User> spec = UserSpecification.filterUsers(keyword, effectiveUnitId, status, roleId);
        return userRepository.findAll(spec, pageable).map(this::mapToUserResponse);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getStudents(String keyword, Pageable pageable) {
        Role studentRole = roleRepository.findByRoleCode("STUDENT")
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy vai trò sinh viên"));
        String scopedUnitId = authHelper.getScopedUnitIdOrNull(); // null = admin
        Specification<User> spec = UserSpecification.filterUsers(keyword, scopedUnitId, "ACTIVE", studentRole.getId());
        return userRepository.findAll(spec, pageable).map(this::mapToUserResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với ID: " + id));
        requireSameUnitOrAdminFor(user);
        return mapToUserResponse(user);
    }

    /**
     * Lấy hồ sơ của user đang đăng nhập từ SecurityContext.
     * Khác với getUserById: endpoint /me chỉ yêu cầu đã đăng nhập, không cần USER_MANAGE.
     */
    @Transactional(readOnly = true)
    public UserResponse getMyProfile() {
        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng hiện tại"));
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Tên đăng nhập đã tồn tại!");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng!");
        }
        if (request.getStudentCode() != null && !request.getStudentCode().isEmpty() && userRepository.existsByStudentCode(request.getStudentCode())) {
            throw new RuntimeException("Mã sinh viên đã tồn tại!");
        }

        // Manager: ép unit của user mới = unit của manager, không cho tạo cho khoa khác.
        // Admin: tôn trọng unitId trong request.
        String targetUnitId = authHelper.isAdmin() ? request.getUnitId() : authHelper.getCurrentUnitId();
        if (!authHelper.isAdmin() && targetUnitId == null) {
            throw new RuntimeException("Tài khoản chưa được gán đơn vị, không thể tạo người dùng");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStudentCode(request.getStudentCode());
        user.setCreditScore(100);
        user.setStatus("ACTIVE");

        if (targetUnitId != null && !targetUnitId.isEmpty()) {
            OrganizationUnit unit = unitRepository.findById(targetUnitId)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đơn vị quản lý!"));
            user.setUnit(unit);
        }

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            Set<Role> roles = new HashSet<>(roleRepository.findAllById(request.getRoleIds()));
            user.setRoles(roles);
        }

        return mapToUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(String id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng!"));
        requireSameUnitOrAdminFor(user);

        if (userRepository.existsByEmail(request.getEmail()) && !user.getEmail().equals(request.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng bởi người dùng khác!");
        }

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStudentCode(request.getStudentCode());

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // Manager: không cho phép chuyển user sang khoa khác (giữ unit hiện tại).
        // Admin: được phép đổi unit theo request.
        if (authHelper.isAdmin()) {
            if (request.getUnitId() != null && !request.getUnitId().isEmpty()) {
                OrganizationUnit unit = unitRepository.findById(request.getUnitId())
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đơn vị quản lý!"));
                user.setUnit(unit);
            } else {
                user.setUnit(null);
            }
        }

        if (request.getRoleIds() != null) {
            Set<Role> roles = new HashSet<>(roleRepository.findAllById(request.getRoleIds()));
            user.setRoles(roles);
        }

        return mapToUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse toggleStatus(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng!"));
        requireSameUnitOrAdminFor(user);

        if ("ACTIVE".equals(user.getStatus())) {
            user.setStatus("LOCKED");
        } else {
            user.setStatus("ACTIVE");
        }

        return mapToUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse approveUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng!"));
        requireSameUnitOrAdminFor(user);
        user.setStatus("ACTIVE");
        User savedUser = userRepository.save(user);

        try {
            emailService.sendApprovalEmail(user.getEmail(), user.getFullName());
        } catch (MessagingException e) {
            log.error("Lỗi khi gửi mail phê duyệt cho {}: {}", user.getEmail(), e.getMessage());
        }

        return mapToUserResponse(savedUser);
    }

    @Transactional
    public UserResponse rejectUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng!"));
        requireSameUnitOrAdminFor(user);
        user.setStatus("REJECTED");
        User savedUser = userRepository.save(user);

        try {
            emailService.sendRejectionEmail(user.getEmail(), user.getFullName());
        } catch (MessagingException e) {
            log.error("Lỗi khi gửi mail từ chối cho {}: {}", user.getEmail(), e.getMessage());
        }

        return mapToUserResponse(savedUser);
    }

    @Transactional
    public String resetPassword(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng!"));
        requireSameUnitOrAdminFor(user);

        String newPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return newPassword;
    }

    @Transactional
    public UserResponse updateCreditScore(String id, CreditScoreRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng!"));
        requireSameUnitOrAdminFor(user);

        int newScore = user.getCreditScore() + request.getAmount();
        user.setCreditScore(Math.max(0, newScore)); // Ensure score doesn't go below 0
        return mapToUserResponse(userRepository.save(user));
    }


    @Transactional
    public void deleteUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng!"));
        requireSameUnitOrAdminFor(user);

        user.setDeletedAt(LocalDateTime.now());
        user.setDeleted(true);
        userRepository.save(user);
    }

    /** Helper: throw nếu user hiện tại không phải admin và target user ở khoa khác. */
    private void requireSameUnitOrAdminFor(User target) {
        authHelper.requireSameUnitOrAdmin(
                target.getUnit() != null ? target.getUnit().getId() : null,
                "người dùng " + target.getUsername());
    }


    private UserResponse mapToUserResponse(User user) {
        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .studentCode(user.getStudentCode())
                .creditScore(user.getCreditScore())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lockedUntil(user.getLockedUntil())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();

        if (user.getUnit() != null) {
            response.setUnit(OrganizationUnitResponse.builder()
                    .id(user.getUnit().getId())
                    .unitName(user.getUnit().getUnitName())
                    .unitType(user.getUnit().getUnitType())
                    .parentId(user.getUnit().getParent() != null ? user.getUnit().getParent().getId() : null)
                    .build());
        }


        if (user.getRoles() != null) {
            response.setRoles(user.getRoles().stream()
                    .map(role -> RoleResponse.builder()
                            .id(role.getId())
                            .roleName(role.getRoleName())
                            .roleCode(role.getRoleCode())
                            .build())
                    .collect(Collectors.toSet()));
        }

        return response;
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
