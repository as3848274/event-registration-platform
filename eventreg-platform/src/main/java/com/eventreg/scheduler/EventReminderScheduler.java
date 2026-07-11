package com.eventreg.scheduler;

import com.eventreg.entity.Event;
import com.eventreg.entity.Registration;
import com.eventreg.entity.enums.RegistrationStatus;
import com.eventreg.repository.EventRepository;
import com.eventreg.repository.RegistrationRepository;
import com.eventreg.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Runs hourly, looks for PUBLISHED events starting within the next `lookahead-hours`
 * window, and batches reminder emails to every CONFIRMED attendee. Hourly cadence
 * (rather than a single fire-at-T-minus-24h job) means a late-created event still
 * gets its reminder, and the query is cheap enough to run that often.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventReminderScheduler {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final EmailService emailService;

    @Value("${app.reminder.lookahead-hours}")
    private int lookaheadHours;

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a");

    @Scheduled(cron = "0 0 * * * *") // top of every hour
    @Transactional(readOnly = true)
    public void sendUpcomingEventReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusHours(lookaheadHours);
        // Narrow window (this hour's slice of the lookahead) avoids re-sending the
        // same reminder every run; in production this would also check a
        // "reminder_sent" flag per registration for full idempotency.
        LocalDateTime windowStart = windowEnd.minusHours(1);

        List<Event> upcoming = eventRepository.findEventsStartingBetween(windowStart, windowEnd);

        for (Event event : upcoming) {
            List<Registration> confirmed = registrationRepository.findByEventId(event.getId()).stream()
                    .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED)
                    .toList();

            String formattedDate = event.getEventDate().format(DISPLAY_FORMAT);
            for (Registration r : confirmed) {
                emailService.sendEventReminder(r.getUser().getEmail(), r.getUser().getName(),
                        event.getTitle(), formattedDate, event.getVenue());
            }
            log.info("Queued {} reminder emails for event {}", confirmed.size(), event.getId());
        }
    }
}
