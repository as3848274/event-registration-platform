package com.eventreg.dto.response;

import com.eventreg.entity.Ticket;
import com.eventreg.entity.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private Long id;
    private String token;
    private TicketStatus status;
    private String eventTitle;
    private String attendeeName;
    private LocalDateTime issuedAt;
    private LocalDateTime checkedInAt;

    public static TicketResponse from(Ticket t) {
        return TicketResponse.builder()
                .id(t.getId())
                .token(t.getToken())
                .status(t.getStatus())
                .eventTitle(t.getRegistration().getEvent().getTitle())
                .attendeeName(t.getRegistration().getUser().getName())
                .issuedAt(t.getIssuedAt())
                .checkedInAt(t.getCheckedInAt())
                .build();
    }
}
