package com.example.i_resource_hub.config;

import com.example.i_resource_hub.security.jwt.AuthEntryPointJWT;
import com.example.i_resource_hub.security.jwt.JWTAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private AuthEntryPointJWT unauthorizedHandler;

    // Khai báo cái Filter quét thẻ vừa tạo
    @Bean
    public JWTAuthFilter authenticationJwtTokenFilter() {
        return new JWTAuthFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                // Xử lý lỗi 401 bằng class vừa tạo
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                // Không lưu session (Vì JWT là Stateless)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // MỞ CỬA cho API Auth (Login, Register)
                        .requestMatchers("/api/auth/**").permitAll()
                        // MỞ CỬA cho Swagger
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // ĐÓNG CỬA TẤT CẢ CÁC API CÒN LẠI (Bắt buộc phải có Token)
                        .anyRequest().authenticated()
                );

        // Chèn Cổng quét thẻ vào trước khi Spring Security xử lý
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}