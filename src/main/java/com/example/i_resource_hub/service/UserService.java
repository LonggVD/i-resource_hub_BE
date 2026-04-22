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
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    @Transactional(readOnly = true)
    public Page<UserResponse> getPageUsers(String keyword, String unitId, String status, String roleId, Pageable pageable) {
        Specification<User> spec = UserSpecification.filterUsers(keyword, unitId, status, roleId);
        return userRepository.findAll(spec, pageable).map(this::mapToUserResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với ID: " + id));
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

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStudentCode(request.getStudentCode());
        user.setCreditScore(100);
        user.setStatus("ACTIVE");

        if (request.getUnitId() != null && !request.getUnitId().isEmpty()) {
            OrganizationUnit unit = unitRepository.findById(request.getUnitId())
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

        if (request.getUnitId() != null && !request.getUnitId().isEmpty()) {
            OrganizationUnit unit = unitRepository.findById(request.getUnitId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đơn vị quản lý!"));
            user.setUnit(unit);
        } else {
            user.setUnit(null);
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
        
        String newPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        return newPassword;
    }

    @Transactional
    public UserResponse updateCreditScore(String id, CreditScoreRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng!"));
        
        int newScore = user.getCreditScore() + request.getAmount();
        user.setCreditScore(Math.max(0, newScore)); // Ensure score doesn't go below 0
        return mapToUserResponse(userRepository.save(user));
    }


    @Transactional
    public void deleteUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng!"));
        
        user.setDeletedAt(LocalDateTime.now());
        user.setDeleted(true);
        userRepository.save(user);
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
