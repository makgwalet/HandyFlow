// security/domain/repository/GuardTokenRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardToken;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuardTokenRepository extends JpaRepository<GuardToken, UUID> {

    /** Validate a token by its JWT jti claim — used by GuardJwtFilter. */
    @Query("""
        SELECT t FROM GuardToken t
        WHERE t.id = :jti
        AND t.revokedAt IS NULL
        AND t.expiresAt > CURRENT_TIMESTAMP
        """)
    Optional<GuardToken> findActive(UUID jti);

    /** All active (non-revoked, non-expired) tokens for a guard. */
    @Query("""
        SELECT t FROM GuardToken t
        WHERE t.guardId = :guardId
        AND t.revokedAt IS NULL
        AND t.expiresAt > CURRENT_TIMESTAMP
        """)
    List<GuardToken> findActiveByGuard(UUID guardId);

    /**
     * Revoke all active tokens for a guard — called when guard status
     * changes to SUSPENDED/TERMINATED or when a supervisor force-revokes.
     */
    @Modifying
    @Query("""
        UPDATE GuardToken t
        SET t.revokedAt    = :now,
            t.revokeReason = :reason
        WHERE t.guardId    = :guardId
        AND t.revokedAt    IS NULL
        """)
    int revokeAllForGuard(UUID guardId, Instant now, String reason);

    /**
     * Purge expired tokens older than the given cutoff.
     * Called by a nightly cleanup job to keep the table small.
     */
    @Modifying
    @Query("DELETE FROM GuardToken t WHERE t.expiresAt < :cutoff")
    int purgeExpiredBefore(Instant cutoff);

    /** All active tokens for a tenant — used by supervisor revoke-all. */
    @Query("""
        SELECT t FROM GuardToken t
        WHERE t.tenantId = :tenantId
        AND t.guardId = :guardId
        AND t.revokedAt IS NULL
        """)
    List<GuardToken> findActiveByTenantAndGuard(TenantId tenantId, UUID guardId);
}
