package com.eventreg.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RegistrationRequest {
    // If > 1, treated as a team registration: this user is the team lead,
    // and (teamSize - 1) placeholder member tickets are reserved alongside theirs.
    @Min(1)
    private Integer teamSize = 1;
}
