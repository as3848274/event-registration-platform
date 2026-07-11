package com.eventreg.scheduler;

import com.eventreg.entity.EmailRetryQueue;
import com.eventreg.entity.enums.EmailRetryStatus;
import com.eventreg.repository.EmailRetryQueueRepository;
import com.eventreg.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reprocesses failed emails from email_retry_queue with backoff. Runs every 2
 * minutes; each entry only gets retried once its own next_retry_at has passed.
 */
@Component
@RequiredArgsConstructor
public class EmailRetryScheduler {

    private final EmailRetryQueueRepository retryQueueRepository;
    private final EmailService emailService;

    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void reprocessFailedEmails() {
        List<EmailRetryQueue> due = retryQueueRepository.findByStatusAndNextRetryAtBefore(
                EmailRetryStatus.PENDING, LocalDateTime.now());
        for (EmailRetryQueue entry : due) {
            emailService.retry(entry);
        }
    }
}
