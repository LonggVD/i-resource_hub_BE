package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.LoginRequest;
import com.example.i_resource_hub.dto.request.SignUpRequest;
import com.example.i_resource_hub.dto.response.JWTResponse;
import com.example.i_resource_hub.entity.OrganizationUnit;
import com.example.i_resource_hub.entity.Role;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.OrganizationUnitRepository;
import com.example.i_resource_hub.repository.RoleRepository;
import com.example.i_resource_hub.repository.UserRepository;
import com.example.i_resource_hub.security.CustomUserDetails;
import com.example.i_resource_hub.util.JWTUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationUnitRepository unitRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTUtils jwtUtils;
    public JWTResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("Lỗi: Tên đăng nhập hoặc mật khẩu không đúng!"));

        if ("PENDING".equals(user.getStatus())) {
            throw new RuntimeException("Lỗi: Tài khoản của bạn đang chờ Quản trị viên phê duyệt!");
        }
        if ("REJECTED".equals(user.getStatus())) {
            throw new RuntimeException("Lỗi: Yêu cầu đăng ký tài khoản của bạn đã bị từ chối!");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Lỗi: Tài khoản của bạn đã bị khóa hoặc vô hiệu hóa!");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwt = jwtUtils.generateJwtToken(authentication);

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            return JWTResponse.builder()
                    .token(jwt)
                    .type("Bearer")
                    .id(userDetails.getId())
                    .username(userDetails.getUsername())
                    .roles(roles)
                    .unitId(userDetails.getUnitId())
                    .dataScope(userDetails.getDataScope())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Lỗi: Tên đăng nhập hoặc mật khẩu không đúng!");
        }
    }

    @Transactional
    public String registerUser(SignUpRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Tên đăng nhập đã tồn tại!");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng!");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setCreditScore(100);
        user.setFailedLoginAttempts(0);
        user.setDeleted(false);

        if ("STUDENT".equalsIgnoreCase(request.getAccountType())) {
            if (request.getStudentCode() == null || request.getStudentCode().isEmpty()) {
                throw new RuntimeException("Sinh viên bắt buộc phải có Mã sinh viên!");
            }
            if (userRepository.existsByStudentCode(request.getStudentCode())) {
                throw new RuntimeException("Mã sinh viên này đã được đăng ký!");
            }

            user.setStudentCode(request.getStudentCode());
            user.setUnit(null);
            user.setStatus("ACTIVE");

            Role studentRole = roleRepository.findByRoleCode("STUDENT")
                    .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy quyền STUDENT"));
            user.setRoles(Collections.singleton(studentRole));

            userRepository.save(user);
            return "Đăng ký tài khoản Sinh viên thành công!";

        } else if ("MANAGER".equalsIgnoreCase(request.getAccountType())) {
            if (request.getUnitId() == null || request.getUnitId().isEmpty()) {
                throw new RuntimeException("Cán bộ quản lý bắt buộc phải chọn Khoa/Phòng ban!");
            }

            OrganizationUnit unit = unitRepository.findById(request.getUnitId())
                    .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy Khoa/Đơn vị."));

            user.setStudentCode(null);
            user.setUnit(unit);
            user.setStatus("PENDING");

            Role managerRole = roleRepository.findByRoleCode("MANAGER")
                    .orElseThrow(() -> new RuntimeException("Lỗi: Không tìm thấy quyền MANAGER"));
            user.setRoles(Collections.singleton(managerRole));

            userRepository.save(user);
            return "Đăng ký tài khoản Quản lý thành công! Vui lòng chờ Quản trị viên phê duyệt.";

        } else {
            throw new RuntimeException("Loại tài khoản không hợp lệ!");
        }
    }
}