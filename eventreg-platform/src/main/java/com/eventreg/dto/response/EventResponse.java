package com.eventreg.dto.response;

import com.eventreg.entity.Event;
import com.eventreg.entity.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private String venue;
    private LocalDateTime eventDate;
    private Integer maxCapacity;
    private Integer waitlistCap;
    private BigDecimal price;
    private EventStatus status;
    private boolean featured;
    private String organiserName;
    private Long organiserId;
    private long confirmedCount;
    private long waitlistedCount;

    public static EventResponse from(Event e, long confirmedCount, long waitlistedCount) {
        return EventResponse.builder()
                .id(e.getId())
                .title(e.getTitle())
                .description(e.getDescription())
                .venue(e.getVenue())
                .eventDate(e.getEventDate())
                .maxCapacity(e.getMaxCapacity())
                .waitlistCap(e.getWaitlistCap())
                .price(e.getPrice())
                .status(e.getStatus())
                .featured(e.isFeatured())
                .organiserName(e.getOrganiser().getName())
                .organiserId(e.getOrganiser().getId())
                .confirmedCount(confirmedCount)
                .waitlistedCount(waitlistedCount)
                .build();
    }
}
