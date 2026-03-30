package com.example.i_resource_hub.entity;

import com.example.i_resource_hub.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", columnDefinition = "CHAR(36)")
    private OrganizationUnit unit;

    @Column(name = "student_code", unique = true, length = 20)
    private String studentCode;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username ;

    @Column(name = "password", nullable = false, length = 255)
    private String password ;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName ;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email ;

    @Column(name = "phone", length = 15)
    private String phone ;

    @Column(name = "credit_score", nullable = false)
    private Integer creditScore = 100;

    @Column(name = "status", nullable = false, length = 255)
    private String status = "ACTIVE"; // ACTIVE, LOCKED

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "failed_login_attempt")
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @CreatedBy
    @Column(name = "created_by", updatable = false, columnDefinition = "CHAR(36)") // Sửa ở đây
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", columnDefinition = "CHAR(36)")
    private String updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", columnDefinition = "CHAR(36)"),
            inverseJoinColumns = @JoinColumn(name = "role_id", columnDefinition = "CHAR(36)"))
    private Set<Role> roles = new HashSet<>();
}
