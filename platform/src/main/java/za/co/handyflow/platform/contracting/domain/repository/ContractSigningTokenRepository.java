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
 *
 * FIX: findByToken(String) was a Spring Data derived query mapping straight
 * to WHERE token = ?, querying the raw column directly — despite V56's own
 * migration comment stating the intent was to look tokens up by
 * SHA-256(token) instead and stop querying the raw column. That never
 * actually happened; this repository is the fix. Callers now hash the
 * incoming token themselves and look it up via findByTokenHash() — see
 * ContractingService.findValidToken().
 */
@Repository
public interface ContractSigningTokenRepository
        extends JpaRepository<ContractSigningToken, UUID> {

    /** Lookup by SHA-256(token) — used to validate every inbound signing request. */
    Optional<ContractSigningToken> findByTokenHash(String tokenHash);

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
