package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.request.LoginRequest;
import com.example.i_resource_hub.dto.response.JWTResponse;
import com.example.i_resource_hub.security.CustomUserDetails;
import com.example.i_resource_hub.util.JWTUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600) // Rất quan trọng: Cho phép Angular gọi API mà không bị lỗi CORS
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    JWTUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {

        // 1. Cô tiếp tân nhờ anh bảo vệ xác thực tài khoản/mật khẩu
        // (Nó sẽ tự động gọi hàm loadUserByUsername mà bạn đã viết ở Bước 3)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        // 2. Xác thực thành công -> Lưu người này vào bộ nhớ an toàn (SecurityContext) của hệ thống
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Chạy máy in thẻ để lấy chuỗi JWT
        String jwt = jwtUtils.generateJwtToken(authentication);

        // 4. Lấy thông tin User (ID, Roles, UnitId...) để nhét vào Response
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        // 5. Gói vào JwtResponse và trả về cho Frontend
        return ResponseEntity.ok(JWTResponse.builder()
                .token(jwt)
                .type("Bearer")
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .roles(roles)
                .unitId(userDetails.getUnitId())
                .dataScope(userDetails.getDataScope())
                .build());
    }
}