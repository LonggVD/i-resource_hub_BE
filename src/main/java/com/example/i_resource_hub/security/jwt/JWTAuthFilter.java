package com.example.i_resource_hub.security.jwt;

import com.example.i_resource_hub.security.UserDetailsServiceImpl;
import com.example.i_resource_hub.util.JWTUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JWTAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JWTUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 1. Lấy token từ request
            String jwt = parseJwt(request);

            // 2. Nếu có token và token chuẩn
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                // Lấy username từ token
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                // Kéo thông tin user từ Database lên
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Xác thực thành công -> Cấp quyền đi tiếp vào hệ thống
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            System.err.println("Không thể thiết lập xác thực người dùng: " + e.getMessage());
        }

        // Cho phép request đi tiếp đến API
        filterChain.doFilter(request, response);
    }

    // Hàm phụ: Tách chữ "Bearer " ra để lấy đúng cái lõi Token
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}