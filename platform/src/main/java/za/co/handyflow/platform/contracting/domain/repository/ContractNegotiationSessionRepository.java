package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ContractNegotiationSessionRepository extends JpaRepository<ContractNegotiationSession, UUID> {
    Optional<ContractNegotiationSession> findBySessionHash(String sessionHash);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM ContractNegotiationSession s WHERE s.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);
}
