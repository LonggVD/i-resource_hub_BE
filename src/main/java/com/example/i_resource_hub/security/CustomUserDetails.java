package com.example.i_resource_hub.security;

import com.example.i_resource_hub.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter // Tự động sinh hàm getId(), getUnitId(), getDataScope()...
@AllArgsConstructor // Tự động sinh constructor nạp tất cả biến
public class CustomUserDetails implements UserDetails {

    private String id;
    private String username;

    @JsonIgnore
    private String password;

    private Collection<? extends GrantedAuthority> authorities;

    private String unitId;
    private String dataScope;

    // Hàm build object từ Entity User
    public static CustomUserDetails build(User user) {

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()))
                .collect(Collectors.toList());

        // Tạm thời fix cứng data scope là SELF
        String scope = "SELF";

        return new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                authorities,
                user.getUnit() != null ? user.getUnit().getId() : null,
                scope
        );
    }

    // --- SỬA LẠI CÁC HÀM OVERRIDE ĐỂ TRẢ VỀ ĐÚNG BIẾN THẬT ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // Mẹo: Sau này bạn có thể check status của User ở đây
    // return user.getStatus().equals("ACTIVE");
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}