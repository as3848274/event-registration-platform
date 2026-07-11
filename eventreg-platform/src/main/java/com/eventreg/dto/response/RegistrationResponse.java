package com.eventreg.dto.response;

import com.eventreg.entity.Registration;
import com.eventreg.entity.enums.RegistrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationResponse {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private RegistrationStatus status;
    private Integer waitlistPosition;
    private LocalDateTime registeredAt;
    private String ticketToken; // null if waitlisted

    public static RegistrationResponse from(Registration r, String ticketToken) {
        return RegistrationResponse.builder()
                .id(r.getId())
                .eventId(r.getEvent().getId())
                .eventTitle(r.getEvent().getTitle())
                .status(r.getStatus())
                .waitlistPosition(r.getWaitlistPosition())
                .registeredAt(r.getRegisteredAt())
                .ticketToken(ticketToken)
                .build();
    }
}
