package com.eventreg.repository;

import com.eventreg.entity.EmailRetryQueue;
import com.eventreg.entity.enums.EmailRetryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailRetryQueueRepository extends JpaRepository<EmailRetryQueue, Long> {
    List<EmailRetryQueue> findByStatusAndNextRetryAtBefore(EmailRetryStatus status, LocalDateTime time);
}
