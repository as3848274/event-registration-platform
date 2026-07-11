package com.eventreg.repository;

import com.eventreg.entity.TeamRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRegistrationRepository extends JpaRepository<TeamRegistration, Long> {
    Optional<TeamRegistration> findByGroupToken(String groupToken);
}
