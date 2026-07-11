package com.eventreg.repository;

import com.eventreg.entity.Event;
import com.eventreg.entity.enums.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Locks the event row for the duration of the transaction (SELECT ... FOR UPDATE).
     * Used during registration to make the "count confirmed seats -> compare to
     * capacity -> insert registration" sequence atomic across concurrent requests.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    List<Event> findByStatusAndDeletedFalse(EventStatus status);

    List<Event> findByOrganiserIdAndDeletedFalse(Long organiserId);

    @Query("select e from Event e where e.status = 'PUBLISHED' and e.deleted = false " +
           "and e.eventDate between :from and :to")
    List<Event> findEventsStartingBetween(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);
}
