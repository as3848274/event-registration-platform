package com.eventreg.entity;

import com.eventreg.entity.enums.EventStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_status", columnList = "status"),
        @Index(name = "idx_event_date", columnList = "event_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organiser_id", nullable = false)
    private User organiser;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Column(name = "waitlist_cap")
    @Builder.Default
    private Integer waitlistCap = 0; // 0 = unlimited waitlist

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Column(name = "refund_eligible", nullable = false)
    @Builder.Default
    private boolean refundEligible = false;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    // Optimistic-locking safety net in addition to the explicit pessimistic
    // lock taken during registration (see RegistrationService).
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
