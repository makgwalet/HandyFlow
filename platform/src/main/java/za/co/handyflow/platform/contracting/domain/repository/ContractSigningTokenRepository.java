package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.contracting.domain.model.ContractSigningToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * File: contracting/domain/repository/ContractSigningTokenRepository.java
 */
@Repository
public interface ContractSigningTokenRepository
        extends JpaRepository<ContractSigningToken, UUID> {

    /** Lookup by token string — used to validate every inbound signing request. */
    Optional<ContractSigningToken> findByToken(String token);

    /**
     * Returns the most recent non-revoked, non-used token for a party.
     * Used when marking a token as used after signing.
     */
    @Query("""
        SELECT t FROM ContractSigningToken t
        WHERE t.partyId = :partyId
          AND t.revokedAt IS NULL
          AND t.usedAt IS NULL
        ORDER BY t.createdAt DESC
        LIMIT 1
    """)
    Optional<ContractSigningToken> findActiveByPartyId(@Param("partyId") UUID partyId);

    /**
     * Returns ALL non-revoked tokens for a party.
     * Used by revokeActiveTokens() on resend — every old token is revoked before
     * a new one is issued.
     */
    @Query("""
        SELECT t FROM ContractSigningToken t
        WHERE t.partyId = :partyId
          AND t.revokedAt IS NULL
    """)
    List<ContractSigningToken> findAllActiveByPartyId(@Param("partyId") UUID partyId);
}
