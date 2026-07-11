package com.eventreg.service;

import com.eventreg.dto.response.RegistrationResponse;
import com.eventreg.dto.response.TeamRegistrationResponse;
import com.eventreg.entity.*;
import com.eventreg.entity.enums.EventStatus;
import com.eventreg.entity.enums.RegistrationStatus;
import com.eventreg.entity.enums.TicketStatus;
import com.eventreg.exception.*;
import com.eventreg.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns the two operations that actually need to be race-safe: reserving a seat and
 * releasing one. Both take a PESSIMISTIC_WRITE lock on the Event row first
 * (EventRepository#findByIdForUpdate), which serializes concurrent requests for the
 * same event at the DB level — the classic "count -> compare -> insert" sequence is
 * safe here because no other transaction can read a stale seat count while this one
 * holds the lock. Different events are unaffected by each other's locks.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final TicketRepository ticketRepository;
    private final TeamRegistrationRepository teamRegistrationRepository;
    private final EmailService emailService;

    @Transactional
    public RegistrationResponse register(User user, Long eventId) {
        Event event = lockAndValidateEvent(eventId);
        assertNoActiveRegistration(user.getId(), eventId);

        Registration registration = reserveOrWaitlist(event, user, null);
        registrationRepository.save(registration);

        if (registration.getStatus() == RegistrationStatus.CONFIRMED) {
            Ticket ticket = issueTicket(registration);
            emailService.sendRegistrationConfirmed(user.getEmail(), user.getName(), event.getTitle(), ticket.getToken());
            return RegistrationResponse.from(registration, ticket.getToken());
        } else {
            emailService.sendWaitlistJoined(user.getEmail(), user.getName(), event.getTitle(), registration.getWaitlistPosition());
            return RegistrationResponse.from(registration, null);
        }
    }

    /**
     * Team registration: one lead registers `teamSize` seats at once. Design decision
     * (undocumented in the original spec, so made explicit here): confirms as many
     * seats as are currently available, waitlists the rest — rather than all-or-nothing.
     * This whole reservation happens under a single event-row lock, so a team's seats
     * are never split by a competing request landing in between.
     *
     * Simplification: since only the lead has an account, every seat in the team is
     * recorded as its own Registration/Ticket row against the lead's user id, linked
     * via TeamRegistration. In a production system you'd collect each member's email
     * up front and send each their own ticket; that's a straightforward extension of
     * this same reservation logic.
     */
    @Transactional
    public TeamRegistrationResponse registerTeam(User lead, Long eventId, int teamSize) {
        if (teamSize < 1) {
            throw new IllegalArgumentException("teamSize must be at least 1");
        }
        Event event = lockAndValidateEvent(eventId);
        assertNoActiveRegistration(lead.getId(), eventId);

        TeamRegistration team = TeamRegistration.builder()
                .leadUser(lead)
                .event(event)
                .memberCount(teamSize)
                .groupToken(UUID.randomUUID().toString())
                .build();
        team = teamRegistrationRepository.save(team);

        List<RegistrationResponse> memberResponses = new ArrayList<>();
        int confirmedSeats = 0;
        int waitlistedSeats = 0;

        for (int i = 0; i < teamSize; i++) {
            Registration registration = reserveOrWaitlist(event, lead, team);
            registrationRepository.save(registration);

            if (registration.getStatus() == RegistrationStatus.CONFIRMED) {
                Ticket ticket = issueTicket(registration);
                confirmedSeats++;
                memberResponses.add(RegistrationResponse.from(registration, ticket.getToken()));
            } else {
                waitlistedSeats++;
                memberResponses.add(RegistrationResponse.from(registration, null));
            }
        }

        emailService.sendRegistrationConfirmed(lead.getEmail(), lead.getName(),
                event.getTitle() + " (team of " + teamSize + ": " + confirmedSeats + " confirmed, "
                        + waitlistedSeats + " waitlisted)", team.getGroupToken());

        return TeamRegistrationResponse.builder()
                .teamRegistrationId(team.getId())
                .groupToken(team.getGroupToken())
                .requestedSize(teamSize)
                .confirmedSeats(confirmedSeats)
                .waitlistedSeats(waitlistedSeats)
                .members(memberResponses)
                .build();
    }

    /**
     * Cancellation + waitlist promotion happen inside the same transaction, under the
     * same event-row lock, so exactly one waitlisted registration is promoted per
     * freed seat — two concurrent cancellations can't both try to promote the same
     * person, and a promotion can't be "lost" between the seat freeing up and someone
     * claiming it.
     */
    @Transactional
    public void cancelRegistration(User requester, Long registrationId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        boolean isOwner = registration.getUser().getId().equals(requester.getId());
        boolean isPrivileged = requester.getRole().name().equals("ADMIN")
                || requester.getRole().name().equals("ORGANISER");
        if (!isOwner && !isPrivileged) {
            throw new UnauthorizedActionException("You cannot cancel this registration");
        }

        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw new InvalidEventStateException("Registration is already cancelled");
        }

        Long eventId = registration.getEvent().getId();
        Event event = lockAndValidateEventForCancellation(eventId);

        boolean wasConfirmed = registration.getStatus() == RegistrationStatus.CONFIRMED;

        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(java.time.LocalDateTime.now());
        registrationRepository.save(registration);

        ticketRepository.findByRegistrationId(registration.getId()).ifPresent(ticket -> {
            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.save(ticket);
        });

        if (wasConfirmed) {
            promoteNextWaitlisted(event);
        }
    }

    // ---- internals ----

    private Event lockAndValidateEvent(Long eventId) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (event.isDeleted() || event.getStatus() != EventStatus.PUBLISHED) {
            throw new InvalidEventStateException("Event is not open for registration");
        }
        return event;
    }

    // Cancellation is allowed even if the event later moved to ONGOING/COMPLETED,
    // so this path doesn't require PUBLISHED — but we still lock the row for the
    // promotion decision.
    private Event lockAndValidateEventForCancellation(Long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
    }

    private void assertNoActiveRegistration(Long userId, Long eventId) {
        registrationRepository.findByUserIdAndEventIdAndStatusIn(
                userId, eventId, List.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
        ).ifPresent(r -> {
            throw new DuplicateRegistrationException("You already have an active registration for this event");
        });
    }

    /**
     * Must be called while holding the event's pessimistic lock. Decides CONFIRMED
     * vs WAITLISTED based on a fresh count taken under that lock.
     */
    private Registration reserveOrWaitlist(Event event, User user, TeamRegistration team) {
        long confirmedCount = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.CONFIRMED);

        if (confirmedCount < event.getMaxCapacity()) {
            return Registration.builder()
                    .user(user)
                    .event(event)
                    .teamRegistration(team)
                    .status(RegistrationStatus.CONFIRMED)
                    .build();
        }

        long waitlistedCount = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.WAITLISTED);
        Integer waitlistCap = event.getWaitlistCap();
        if (waitlistCap != null && waitlistCap > 0 && waitlistedCount >= waitlistCap) {
            throw new EventFullException("Event and waitlist are both full");
        }

        return Registration.builder()
                .user(user)
                .event(event)
                .teamRegistration(team)
                .status(RegistrationStatus.WAITLISTED)
                .waitlistPosition((int) waitlistedCount + 1)
                .build();
    }

    /** Must be called while holding the event's pessimistic lock. FIFO promotion. */
    private void promoteNextWaitlisted(Event event) {
        List<Registration> queue = registrationRepository.findWaitlistQueue(event.getId());
        if (queue.isEmpty()) return;

        Registration next = queue.get(0);
        next.setStatus(RegistrationStatus.CONFIRMED);
        next.setWaitlistPosition(null);
        registrationRepository.save(next);

        Ticket ticket = issueTicket(next);
        emailService.sendWaitlistPromoted(next.getUser().getEmail(), next.getUser().getName(),
                event.getTitle(), ticket.getToken());
    }

    private Ticket issueTicket(Registration registration) {
        Ticket ticket = Ticket.builder()
                .registration(registration)
                .token(UUID.randomUUID().toString())
                .status(TicketStatus.CONFIRMED)
                .build();
        return ticketRepository.save(ticket);
    }
}
