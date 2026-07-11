package com.eventreg.service;

import com.eventreg.dto.response.TicketResponse;
import com.eventreg.entity.Ticket;
import com.eventreg.entity.User;
import com.eventreg.entity.enums.TicketStatus;
import com.eventreg.exception.InvalidTokenException;
import com.eventreg.exception.ResourceNotFoundException;
import com.eventreg.exception.UnauthorizedActionException;
import com.eventreg.repository.TicketRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public TicketResponse getByToken(String token) {
        Ticket ticket = ticketRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        return TicketResponse.from(ticket);
    }

    /**
     * Idempotent check-in: scanning an already-USED ticket returns the same success
     * response instead of an error, so a flaky scanner or an entry-gate retry never
     * surfaces as a failure to the organiser. Only a CANCELLED ticket is rejected.
     */
    @Transactional
    public TicketResponse checkIn(String token, User organiserOrAdmin) {
        Ticket ticket = ticketRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid ticket token"));

        boolean isOrganiser = organiserOrAdmin.getRole().name().equals("ORGANISER")
                && ticket.getRegistration().getEvent().getOrganiser().getId().equals(organiserOrAdmin.getId());
        boolean isAdmin = organiserOrAdmin.getRole().name().equals("ADMIN");
        if (!isOrganiser && !isAdmin) {
            throw new UnauthorizedActionException("You are not the organiser of this event");
        }

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new InvalidTokenException("This ticket has been cancelled and cannot be used for entry");
        }

        if (ticket.getStatus() == TicketStatus.CONFIRMED) {
            ticket.setStatus(TicketStatus.USED);
            ticket.setCheckedInAt(LocalDateTime.now());
            ticketRepository.save(ticket);
        }
        // If already USED, fall through and return 200 with the existing check-in time
        // instead of throwing — that's the idempotency guarantee.

        return TicketResponse.from(ticket);
    }

    public byte[] generateQrCode(String token) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(token, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}
