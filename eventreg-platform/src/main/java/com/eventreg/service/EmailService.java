package com.eventreg.service;

import com.eventreg.entity.EmailRetryQueue;
import com.eventreg.entity.enums.EmailRetryStatus;
import com.eventreg.repository.EmailRetryQueueRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * All outbound mail goes through here. On failure (SMTP down, transient network
 * error, etc.) the message is persisted to email_retry_queue instead of being lost,
 * and a scheduled reprocessor (EmailRetryScheduler) picks it back up with backoff.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailRetryQueueRepository retryQueueRepository;

    @Value("${app.mail.from}")
    private String fromAddress;

    private static final int MAX_ATTEMPTS = 5;

    @Async("emailTaskExecutor")
    public void sendAsync(String to, String subject, String htmlBody) {
        try {
            sendNow(to, subject, htmlBody);
        } catch (Exception e) {
            log.warn("Email send failed for {} ('{}'), queuing for retry: {}", to, subject, e.getMessage());
            queueForRetry(to, subject, htmlBody);
        }
    }

    public void sendNow(String to, String subject, String htmlBody) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }

    private void queueForRetry(String to, String subject, String body) {
        EmailRetryQueue entry = EmailRetryQueue.builder()
                .recipient(to)
                .subject(subject)
                .body(body)
                .attempts(0)
                .status(EmailRetryStatus.PENDING)
                .nextRetryAt(LocalDateTime.now().plusMinutes(2))
                .build();
        retryQueueRepository.save(entry);
    }

    /**
     * Called by the scheduled reprocessor. Exponential-ish backoff: 2, 4, 8, 16, 32 min.
     */
    public void retry(EmailRetryQueue entry) {
        try {
            sendNow(entry.getRecipient(), entry.getSubject(), entry.getBody());
            entry.setStatus(EmailRetryStatus.SENT);
            retryQueueRepository.save(entry);
        } catch (Exception e) {
            int attempts = entry.getAttempts() + 1;
            entry.setAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                entry.setStatus(EmailRetryStatus.FAILED_PERMANENTLY);
                log.error("Email to {} permanently failed after {} attempts", entry.getRecipient(), attempts);
            } else {
                entry.setNextRetryAt(LocalDateTime.now().plusMinutes((long) Math.pow(2, attempts)));
            }
            retryQueueRepository.save(entry);
        }
    }

    // ---- Templated convenience methods ----

    public void sendVerificationEmail(String to, String name, String verifyLink) {
        String body = "<p>Hi " + name + ",</p>"
                + "<p>Welcome! Please verify your email by clicking the link below (expires in 15 minutes):</p>"
                + "<p><a href=\"" + verifyLink + "\">Verify Email</a></p>";
        sendAsync(to, "Verify your email", body);
    }

    public void sendPasswordResetEmail(String to, String name, String resetLink) {
        String body = "<p>Hi " + name + ",</p>"
                + "<p>We received a request to reset your password. This link expires in 15 minutes:</p>"
                + "<p><a href=\"" + resetLink + "\">Reset Password</a></p>"
                + "<p>If you didn't request this, you can ignore this email.</p>";
        sendAsync(to, "Password reset request", body);
    }

    public void sendRegistrationConfirmed(String to, String name, String eventTitle, String ticketToken) {
        String body = "<p>Hi " + name + ",</p>"
                + "<p>You're confirmed for <b>" + eventTitle + "</b>! Your ticket token is:</p>"
                + "<p><code>" + ticketToken + "</code></p>";
        sendAsync(to, "Registration confirmed: " + eventTitle, body);
    }

    public void sendWaitlistJoined(String to, String name, String eventTitle, int position) {
        String body = "<p>Hi " + name + ",</p>"
                + "<p><b>" + eventTitle + "</b> is currently full. You've been added to the waitlist "
                + "at position " + position + ". We'll email you if a seat opens up.</p>";
        sendAsync(to, "Waitlisted: " + eventTitle, body);
    }

    public void sendWaitlistPromoted(String to, String name, String eventTitle, String ticketToken) {
        String body = "<p>Hi " + name + ",</p>"
                + "<p>Good news! A seat opened up in <b>" + eventTitle + "</b> and you're now confirmed. "
                + "Your ticket token is:</p><p><code>" + ticketToken + "</code></p>";
        sendAsync(to, "You're off the waitlist: " + eventTitle, body);
    }

    public void sendEventCancelled(String to, String name, String eventTitle, boolean refundEligible) {
        String body = "<p>Hi " + name + ",</p>"
                + "<p><b>" + eventTitle + "</b> has been cancelled by the organiser.</p>"
                + (refundEligible ? "<p>You are eligible for a refund. Our team will follow up shortly.</p>" : "");
        sendAsync(to, "Event cancelled: " + eventTitle, body);
    }

    public void sendEventReminder(String to, String name, String eventTitle, String eventDate, String venue) {
        String body = "<p>Hi " + name + ",</p>"
                + "<p>Reminder: <b>" + eventTitle + "</b> is coming up on " + eventDate + " at " + venue + ".</p>";
        sendAsync(to, "Reminder: " + eventTitle + " is tomorrow", body);
    }
}
