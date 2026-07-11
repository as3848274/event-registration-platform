package com.eventreg.repository;

import com.eventreg.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByToken(String token);
    Optional<Ticket> findByRegistrationId(Long registrationId);
}
