package com.eventreg.controller;

import com.eventreg.dto.response.TicketResponse;
import com.eventreg.security.AppUserPrincipal;
import com.eventreg.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/{token}")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable String token) {
        return ResponseEntity.ok(ticketService.getByToken(token));
    }

    @GetMapping(value = "/{token}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(@PathVariable String token) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(ticketService.generateQrCode(token));
    }

    // Organiser/Admin scans this at the door.
    @PostMapping("/{token}/checkin")
    public ResponseEntity<TicketResponse> checkIn(@AuthenticationPrincipal AppUserPrincipal principal,
                                                    @PathVariable String token) {
        return ResponseEntity.ok(ticketService.checkIn(token, principal.getUser()));
    }
}
