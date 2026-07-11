package com.eventreg.service;

import com.eventreg.dto.response.EventStatsResponse;
import com.eventreg.entity.Event;
import com.eventreg.entity.Registration;
import com.eventreg.entity.enums.RegistrationStatus;
import com.eventreg.entity.enums.TicketStatus;
import com.eventreg.exception.ResourceNotFoundException;
import com.eventreg.repository.EventRepository;
import com.eventreg.repository.RegistrationRepository;
import com.eventreg.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public EventStatsResponse getEventStats(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        List<Registration> registrations = registrationRepository.findByEventId(eventId);

        long confirmed = registrations.stream().filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED).count();
        long waitlisted = registrations.stream().filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED).count();
        long cancelled = registrations.stream().filter(r -> r.getStatus() == RegistrationStatus.CANCELLED).count();

        long checkedIn = registrations.stream()
                .map(r -> ticketRepository.findByRegistrationId(r.getId()).orElse(null))
                .filter(t -> t != null && t.getStatus() == TicketStatus.USED)
                .count();

        double checkInRate = confirmed == 0 ? 0.0 : (checkedIn * 100.0) / confirmed;
        BigDecimal revenue = event.getPrice().multiply(BigDecimal.valueOf(confirmed));

        return EventStatsResponse.builder()
                .eventId(event.getId())
                .eventTitle(event.getTitle())
                .totalRegistrations(registrations.size())
                .confirmedCount(confirmed)
                .waitlistedCount(waitlisted)
                .cancelledCount(cancelled)
                .checkedInCount(checkedIn)
                .checkInRatePercent(Math.round(checkInRate * 100.0) / 100.0)
                .revenue(revenue)
                .build();
    }
}
