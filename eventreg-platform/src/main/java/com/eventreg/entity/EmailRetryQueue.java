package com.eventreg.entity;

import com.eventreg.entity.enums.EmailRetryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_retry_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailRetryQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EmailRetryStatus status = EmailRetryStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.nextRetryAt == null) {
            this.nextRetryAt = LocalDateTime.now();
        }
    }
}
