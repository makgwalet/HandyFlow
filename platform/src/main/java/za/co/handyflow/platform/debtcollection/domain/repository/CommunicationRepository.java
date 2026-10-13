package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.Communication;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationRepository extends JpaRepository<Communication, UUID> {

    @Query("SELECT c FROM Communication c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<Communication> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT c FROM Communication c WHERE c.tenantId = :tenantId AND c.caseId = :caseId ORDER BY c.createdAt DESC")
    List<Communication> findByCaseId(TenantId tenantId, UUID caseId);

    @Query("SELECT c FROM Communication c WHERE c.tenantId = :tenantId AND c.idempotencyKey = :key")
    Optional<Communication> findByIdempotencyKey(TenantId tenantId, String key);

    /** Inbound dedupe: the same provider message id must not be recorded twice for a tenant. */
    @Query("SELECT c FROM Communication c WHERE c.tenantId = :tenantId AND c.direction = 'INBOUND' AND c.providerMessageId = :providerMessageId")
    Optional<Communication> findInboundByProviderMessageId(TenantId tenantId, String providerMessageId);

    /** Provider callbacks carry no tenant — a List, so an ambiguous id can be detected and ignored rather than mis-applied. */
    @Query("SELECT c FROM Communication c WHERE c.direction = 'OUTBOUND' AND c.providerMessageId = :providerMessageId")
    List<Communication> findOutboundByProviderMessageId(String providerMessageId);

    /** Messages that actually left the building today (not blocked/failed), non-letter — feeds the daily contact cap. */
    @Query("""
        SELECT COUNT(c) FROM Communication c WHERE c.tenantId = :tenantId AND c.caseId = :caseId
        AND c.direction = 'OUTBOUND' AND c.channel <> 'LETTER' AND c.status NOT IN ('BLOCKED','FAILED')
        AND c.createdAt >= :since
        """)
    long countOutboundSince(TenantId tenantId, UUID caseId, Instant since);
}
