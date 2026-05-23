package com.example.i_resource_hub.entity;

import com.example.i_resource_hub.entity.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "penalties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Penalty extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", columnDefinition = "CHAR(36)")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", columnDefinition = "CHAR(36)")
    private Booking booking;

    @Column(name = "penalty_type", length = 50)
    private String penaltyType; // OVERDUE, DAMAGE, LOST

    @Column(name = "penalty_point", nullable = false)
    private Integer penaltyPoint;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, REVOKED, PAID

    @Column(name = "paid_at")
    private java.time.LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user", columnDefinition = "CHAR(36)")
    private User createdByUser;

    @OneToMany(mappedBy = "penalty", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<PenaltyEvidence> evidences;

    @Column(name = "fine_amount")
    private Double fineAmount;

    @Column(name = "requires_review")
    private Boolean requiresReview = false;

    @Column(name = "review_status", length = 20)
    private String reviewStatus; // PENDING, SUBMITTED, APPROVED
}
