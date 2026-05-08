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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

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

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
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
}
