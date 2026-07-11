package com.eventreg.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamRegistrationResponse {
    private Long teamRegistrationId;
    private String groupToken;
    private int requestedSize;
    private int confirmedSeats;
    private int waitlistedSeats;
    private List<RegistrationResponse> members;
}
