package com.example.i_resource_hub.security.jwt;

import com.example.i_resource_hub.security.CustomUserDetails;
import com.example.i_resource_hub.security.UserDetailsServiceImpl;
import com.example.i_resource_hub.util.JWTUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

/**
 * Xác thực JWT khi client gửi STOMP CONNECT.
 *  - Client gắn header `Authorization: Bearer <token>` trên CONNECT frame
 *  - Interceptor parse, validate, set user Principal
 *  - Nhờ vậy Spring tự route /user/<username>/queue/notifications đúng
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JWTUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * Map mỗi topic protected → bộ authority chấp nhận được (chỉ cần khớp 1).
     * Topic không nằm trong map: không cần check thêm (mặc định cho phép sau khi CONNECT auth).
     */
    private static final Set<String> MANAGER_AUTHORITIES = Set.of(
            "ADMIN", "ROLE_ADMIN", "RESOURCE_MANAGE", "ROLE_MANAGER");
    private static final Map<String, Set<String>> PROTECTED_TOPICS = Map.of(
            "/topic/dashboard", MANAGER_AUTHORITIES,
            "/topic/bookings-board-changed", Set.of("BOOKING_APPROVE"));

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
            return message;
        }

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("STOMP CONNECT thiếu Authorization header");
            return message;
        }

        String token = authHeader.substring(7);
        try {
            if (jwtUtils.validateJwtToken(token)) {
                String username = jwtUtils.getUserNameFromJwtToken(token);
                CustomUserDetails userDetails =
                        (CustomUserDetails) userDetailsService.loadUserByUsername(username);

                Authentication auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                accessor.setUser(auth);
                log.debug("STOMP CONNECT xác thực thành công cho user {}", username);
            }
        } catch (Exception e) {
            log.warn("STOMP CONNECT xác thực thất bại: {}", e.getMessage());
        }

        return message;
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;
        Set<String> required = PROTECTED_TOPICS.get(destination);
        if (required == null) return;

        Principal user = accessor.getUser();
        if (!(user instanceof Authentication auth) || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Cần đăng nhập để subscribe " + destination);
        }
        boolean allowed = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(required::contains);
        if (!allowed) {
            throw new AccessDeniedException("Không có quyền subscribe " + destination);
        }
    }
}
