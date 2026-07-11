package com.eventreg.service;

import com.eventreg.dto.request.EventCreateRequest;
import com.eventreg.dto.request.EventUpdateRequest;
import com.eventreg.dto.response.EventResponse;
import com.eventreg.entity.Event;
import com.eventreg.entity.Registration;
import com.eventreg.entity.User;
import com.eventreg.entity.enums.EventStatus;
import com.eventreg.entity.enums.RegistrationStatus;
import com.eventreg.exception.InvalidEventStateException;
import com.eventreg.exception.ResourceNotFoundException;
import com.eventreg.exception.UnauthorizedActionException;
import com.eventreg.repository.EventRepository;
import com.eventreg.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final EmailService emailService;

    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public EventResponse createEvent(User organiser, EventCreateRequest request) {
        Event event = Event.builder()
                .organiser(organiser)
                .title(request.getTitle())
                .description(request.getDescription())
                .venue(request.getVenue())
                .eventDate(request.getEventDate())
                .maxCapacity(request.getMaxCapacity())
                .waitlistCap(request.getWaitlistCap() == null ? 0 : request.getWaitlistCap())
                .price(request.getPrice())
                .status(EventStatus.DRAFT)
                .build();
        event = eventRepository.save(event);
        return toResponse(event);
    }

    @Transactional
    @CacheEvict(value = {"events", "eventDetail"}, allEntries = true)
    public EventResponse updateEvent(User requester, Long eventId, EventUpdateRequest request) {
        Event event = getOwnedEvent(requester, eventId);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStateException("Only DRAFT events can be edited. Cancel and recreate instead.");
        }

        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getVenue() != null) event.setVenue(request.getVenue());
        if (request.getEventDate() != null) event.setEventDate(request.getEventDate());
        if (request.getMaxCapacity() != null) event.setMaxCapacity(request.getMaxCapacity());
        if (request.getWaitlistCap() != null) event.setWaitlistCap(request.getWaitlistCap());
        if (request.getPrice() != null) event.setPrice(request.getPrice());

        return toResponse(eventRepository.save(event));
    }

    @Transactional
    @CacheEvict(value = {"events", "eventDetail"}, allEntries = true)
    public EventResponse publish(User requester, Long eventId) {
        Event event = getOwnedEvent(requester, eventId);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStateException("Only DRAFT events can be published");
        }
        event.setStatus(EventStatus.PUBLISHED);
        return toResponse(eventRepository.save(event));
    }

    @Transactional
    @CacheEvict(value = {"events", "eventDetail"}, allEntries = true)
    public EventResponse unpublish(User requester, Long eventId) {
        Event event = getOwnedEvent(requester, eventId);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new InvalidEventStateException("Only PUBLISHED events can be unpublished");
        }
        event.setStatus(EventStatus.DRAFT);
        return toResponse(eventRepository.save(event));
    }

    /**
     * Cancelling an event notifies every active (confirmed + waitlisted) registrant
     * and flags refund eligibility. This does NOT touch individual registration rows —
     * ticket/registration status transitions on cancellation are a deliberate follow-up
     * (kept simple here: the event status is authoritative for "is this still valid").
     */
    @Transactional
    @CacheEvict(value = {"events", "eventDetail"}, allEntries = true)
    public EventResponse cancelEvent(User requester, Long eventId, boolean refundEligible) {
        Event event = getOwnedEvent(requester, eventId);
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new InvalidEventStateException("Event is already " + event.getStatus());
        }
        event.setStatus(EventStatus.CANCELLED);
        event.setRefundEligible(refundEligible);
        eventRepository.save(event);

        List<Registration> active = registrationRepository.findByEventId(eventId).stream()
                .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED)
                .toList();
        for (Registration r : active) {
            emailService.sendEventCancelled(r.getUser().getEmail(), r.getUser().getName(),
                    event.getTitle(), refundEligible);
        }
        return toResponse(event);
    }

    @Transactional
    @CacheEvict(value = {"events", "eventDetail"}, allEntries = true)
    public void softDeleteEvent(User requester, Long eventId) {
        Event event = getOwnedEvent(requester, eventId);
        event.setDeleted(true);
        eventRepository.save(event);
    }

    @Transactional
    @CacheEvict(value = {"events", "eventDetail"}, allEntries = true)
    public EventResponse setFeatured(Long eventId, boolean featured) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        event.setFeatured(featured);
        return toResponse(eventRepository.save(event));
    }

    @Cacheable(value = "eventDetail", key = "#eventId")
    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        return toResponse(event);
    }

    @Cacheable(value = "events", key = "'published'")
    @Transactional(readOnly = true)
    public List<EventResponse> listPublishedEvents() {
        return eventRepository.findByStatusAndDeletedFalse(EventStatus.PUBLISHED)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listMyEvents(User organiser) {
        return eventRepository.findByOrganiserIdAndDeletedFalse(organiser.getId())
                .stream().map(this::toResponse).toList();
    }

    private Event getOwnedEvent(User requester, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        boolean isAdmin = requester.getRole().name().equals("ADMIN");
        if (!isAdmin && !event.getOrganiser().getId().equals(requester.getId())) {
            throw new UnauthorizedActionException("You do not own this event");
        }
        return event;
    }

    private EventResponse toResponse(Event event) {
        long confirmed = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.CONFIRMED);
        long waitlisted = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.WAITLISTED);
        return EventResponse.from(event, confirmed, waitlisted);
    }
}
