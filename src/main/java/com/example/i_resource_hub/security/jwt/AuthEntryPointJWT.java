package com.example.i_resource_hub.security.jwt;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthEntryPointJWT implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        // Trả về lỗi 401 khi chưa đăng nhập mà đòi gọi API
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Lỗi: Chưa xác thực (Token không hợp lệ hoặc bị thiếu)");
    }
}