package com.eventreg.repository;

import com.eventreg.entity.Registration;
import com.eventreg.entity.enums.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

    Optional<Registration> findByUserIdAndEventIdAndStatusIn(
            Long userId, Long eventId, List<RegistrationStatus> statuses);

    // Earliest-registered waitlisted entry for an event -> next to promote (FIFO).
    @Query("select r from Registration r where r.event.id = :eventId " +
           "and r.status = 'WAITLISTED' order by r.waitlistPosition asc")
    List<Registration> findWaitlistQueue(@Param("eventId") Long eventId);

    List<Registration> findByEventId(Long eventId);

    List<Registration> findByUserId(Long userId);
}
