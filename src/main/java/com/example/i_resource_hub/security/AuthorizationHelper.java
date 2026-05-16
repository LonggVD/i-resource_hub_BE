package com.example.i_resource_hub.security;

import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Helper RBAC theo đơn vị (unit / khoa):
 *  - ADMIN: toàn hệ thống — không filter.
 *  - Còn lại (manager/giáo vụ): chỉ trong unit của mình.
 *
 * Dùng pattern:
 *  - {@link #getScopedUnitIdOrNull()} cho READ — null = không filter (admin).
 *  - {@link #requireSameUnitOrAdmin(String, String)} cho WRITE — throw nếu cross-unit.
 */
@Component
@RequiredArgsConstructor
public class AuthorizationHelper {

    public static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final UserRepository userRepository;

    /** SecurityContext user details (đã có sẵn trong JWT, không hit DB). */
    public CustomUserDetails getCurrentUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new AccessDeniedException("Chưa đăng nhập");
        }
        return details;
    }

    public String getCurrentUserId() {
        return getCurrentUserDetails().getId();
    }

    /** Unit của user hiện tại (có thể null nếu user không gắn unit). */
    public String getCurrentUnitId() {
        return getCurrentUserDetails().getUnitId();
    }

    /** Hit DB để lấy entity User đầy đủ — dùng khi cần roles/email/... */
    public User getCurrentUser() {
        String id = getCurrentUserId();
        return userRepository.findById(id)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy người dùng hiện tại"));
    }

    public boolean isAdmin() {
        return getCurrentUserDetails().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_ROLE::equals);
    }

    /**
     * Trả về unitId để filter READ query:
     *  - null nếu user là ADMIN (không cần filter)
     *  - unitId của user nếu là manager/giáo vụ
     *
     * Repository có thể dùng JPQL pattern: WHERE (:unitId IS NULL OR x.unit.id = :unitId)
     */
    public String getScopedUnitIdOrNull() {
        if (isAdmin()) return null;
        return getCurrentUnitId();
    }

    /**
     * Đảm bảo user hiện tại được phép thao tác trên tài nguyên thuộc unit `targetUnitId`.
     * - ADMIN: pass.
     * - Manager: pass nếu unit khớp; throw AccessDeniedException nếu không.
     * - `targetUnitId` null: throw để tránh "leak" (tài nguyên không thuộc unit nào — không
     *   ai ngoài admin nên đụng vào).
     */
    public void requireSameUnitOrAdmin(String targetUnitId, String resourceLabel) {
        if (isAdmin()) return;
        String myUnitId = getCurrentUnitId();
        if (myUnitId == null) {
            throw new AccessDeniedException(
                    "Tài khoản chưa được gán đơn vị, không thể thao tác " + resourceLabel);
        }
        if (targetUnitId == null || !Objects.equals(myUnitId, targetUnitId)) {
            throw new AccessDeniedException(
                    "Không có quyền với " + resourceLabel + " thuộc đơn vị khác");
        }
    }
}
