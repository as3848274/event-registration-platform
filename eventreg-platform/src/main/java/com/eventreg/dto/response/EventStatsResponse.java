package com.eventreg.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStatsResponse {
    private Long eventId;
    private String eventTitle;
    private long totalRegistrations;
    private long confirmedCount;
    private long waitlistedCount;
    private long cancelledCount;
    private long checkedInCount;
    private double checkInRatePercent;
    private BigDecimal revenue;
}
