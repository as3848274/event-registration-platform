package com.eventreg.entity;

import com.eventreg.entity.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "registrations", indexes = {
        @Index(name = "idx_reg_event_status", columnList = "event_id,status"),
        @Index(name = "idx_reg_user_event", columnList = "user_id,event_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // Set when this registration is part of a team/group registration.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_registration_id")
    private TeamRegistration teamRegistration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    // Position in the waitlist queue (lower = earlier). Null when not waitlisted.
    @Column(name = "waitlist_position")
    private Integer waitlistPosition;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        this.registeredAt = LocalDateTime.now();
    }
}
