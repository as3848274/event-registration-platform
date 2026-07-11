package com.eventreg.controller;

import com.eventreg.dto.request.EventCreateRequest;
import com.eventreg.dto.request.EventUpdateRequest;
import com.eventreg.dto.response.EventResponse;
import com.eventreg.security.AppUserPrincipal;
import com.eventreg.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                 @Valid @RequestBody EventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(principal.getUser(), request));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> listPublished() {
        return ResponseEntity.ok(eventService.listPublishedEvents());
    }

    @GetMapping("/mine")
    public ResponseEntity<List<EventResponse>> listMine(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(eventService.listMyEvents(principal.getUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> update(@AuthenticationPrincipal AppUserPrincipal principal,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody EventUpdateRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(principal.getUser(), id, request));
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<EventResponse> publish(@AuthenticationPrincipal AppUserPrincipal principal,
                                                  @PathVariable Long id) {
        return ResponseEntity.ok(eventService.publish(principal.getUser(), id));
    }

    @PatchMapping("/{id}/unpublish")
    public ResponseEntity<EventResponse> unpublish(@AuthenticationPrincipal AppUserPrincipal principal,
                                                     @PathVariable Long id) {
        return ResponseEntity.ok(eventService.unpublish(principal.getUser(), id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<EventResponse> cancel(@AuthenticationPrincipal AppUserPrincipal principal,
                                                  @PathVariable Long id,
                                                  @RequestParam(defaultValue = "false") boolean refundEligible) {
        return ResponseEntity.ok(eventService.cancelEvent(principal.getUser(), id, refundEligible));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal,
                                        @PathVariable Long id) {
        eventService.softDeleteEvent(principal.getUser(), id);
        return ResponseEntity.noContent().build();
    }
}
