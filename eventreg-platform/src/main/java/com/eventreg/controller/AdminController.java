package com.eventreg.controller;

import com.eventreg.dto.response.EventResponse;
import com.eventreg.dto.response.EventStatsResponse;
import com.eventreg.service.AnalyticsService;
import com.eventreg.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AnalyticsService analyticsService;
    private final EventService eventService;

    @GetMapping("/events/{id}/stats")
    public EventStatsResponse getStats(@PathVariable Long id) {
        return analyticsService.getEventStats(id);
    }

    @PatchMapping("/events/{id}/feature")
    public EventResponse setFeatured(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean featured) {
        return eventService.setFeatured(id, featured);
    }
}
