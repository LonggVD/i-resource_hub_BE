package com.example.i_resource_hub.entity;

import com.example.i_resource_hub.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private OrganizationUnit unit ;

    @Column(name = "student_code", unique = true, nullable = false, length = 20)
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

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE, LOCKED

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}

