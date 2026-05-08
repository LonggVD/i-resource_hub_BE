package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.response.NotificationResponse;
import com.example.i_resource_hub.entity.Notification;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.NotificationRepository;
import com.example.i_resource_hub.repository.UserRepository;
import com.example.i_resource_hub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String USER_QUEUE = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ===== Tạo & push real-time =====

    /**
     * Tạo notification + push qua WebSocket nếu user đang online.
     * Idempotent: KHÔNG check trùng — caller chịu trách nhiệm chỉ gọi 1 lần cho 1 sự kiện.
     */
    @Transactional
    public Notification createAndPush(User user, String type, String referenceId,
                                      String title, String content) {
        if (user == null) return null;

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .referenceId(referenceId)
                .title(title)
                .content(content)
                .isPushSent(false)
                .build();
        notification = notificationRepository.save(notification);

        // Push real-time qua STOMP. Nếu user offline, sẽ thấy khi load /my next time.
        try {
            NotificationResponse payload = toResponse(notification);
            messagingTemplate.convertAndSendToUser(
                    user.getUsername(), USER_QUEUE, payload);
            notification.setIsPushSent(true);
            notificationRepository.save(notification);
            log.debug("Pushed notification to {}: {}", user.getUsername(), title);
        } catch (Exception e) {
            log.warn("Push notification thất bại cho user {}: {}", user.getUsername(), e.getMessage());
        }

        return notification;
    }

    // ===== REST endpoints data =====

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        User current = getCurrentUser();
        return notificationRepository
                .findByUser_IdAndIsDeletedFalseOrderByCreatedAtDesc(current.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getMyUnreadCount() {
        User current = getCurrentUser();
        return notificationRepository.countByUser_IdAndReadAtIsNullAndIsDeletedFalse(current.getId());
    }

    @Transactional
    public void markAsRead(String notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo"));
        User current = getCurrentUser();
        if (!n.getUser().getId().equals(current.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Không thể đánh dấu thông báo của người khác");
        }
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
    }

    @Transactional
    public int markAllAsRead() {
        User current = getCurrentUser();
        return notificationRepository.markAllAsRead(current.getId(), LocalDateTime.now());
    }

    @Transactional
    public void deleteNotification(String notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo"));
        User current = getCurrentUser();
        if (!n.getUser().getId().equals(current.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Không thể xoá thông báo của người khác");
        }
        n.setDeleted(true);
        notificationRepository.save(n);
    }

    // ===== Helpers =====

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUser() != null ? n.getUser().getId() : null)
                .type(n.getType())
                .referenceId(n.getReferenceId())
                .title(n.getTitle())
                .content(n.getContent())
                .read(n.getReadAt() != null)
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private User getCurrentUser() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng hiện tại"));
    }
}
