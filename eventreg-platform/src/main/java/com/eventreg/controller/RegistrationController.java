package com.eventreg.controller;

import com.eventreg.dto.request.RegistrationRequest;
import com.eventreg.dto.response.RegistrationResponse;
import com.eventreg.dto.response.TeamRegistrationResponse;
import com.eventreg.security.AppUserPrincipal;
import com.eventreg.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/api/events/{id}/register")
    public ResponseEntity<?> register(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable("id") Long eventId,
                                       @RequestBody(required = false) RegistrationRequest request) {
        int teamSize = (request == null || request.getTeamSize() == null) ? 1 : request.getTeamSize();

        if (teamSize == 1) {
            RegistrationResponse response = registrationService.register(principal.getUser(), eventId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        TeamRegistrationResponse response = registrationService.registerTeam(principal.getUser(), eventId, teamSize);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/api/registrations/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AppUserPrincipal principal,
                                        @PathVariable Long id) {
        registrationService.cancelRegistration(principal.getUser(), id);
        return ResponseEntity.noContent().build();
    }
}
