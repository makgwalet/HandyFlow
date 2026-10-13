package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationOptOut;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationOptOutRepository extends JpaRepository<CommunicationOptOut, UUID> {

    @Query("SELECT o FROM CommunicationOptOut o WHERE o.tenantId = :tenantId AND o.id = :id")
    Optional<CommunicationOptOut> findByTenantIdAndId(TenantId tenantId, UUID id);

    /** Any ACTIVE opt-out that covers this channel (an all-channel entry, or one for exactly this channel). */
    @Query("""
        SELECT o FROM CommunicationOptOut o WHERE o.tenantId = :tenantId AND o.contactValue = :value
        AND o.revokedAt IS NULL AND (o.channel IS NULL OR o.channel = :channel)
        """)
    List<CommunicationOptOut> findActiveCovering(TenantId tenantId, String value, CommunicationChannel channel);

    /** Exact (value, channel) active entry — null channel matches only all-channel entries. Used to keep recording idempotent. */
    @Query("""
        SELECT o FROM CommunicationOptOut o WHERE o.tenantId = :tenantId AND o.contactValue = :value
        AND o.revokedAt IS NULL
        AND ((:channel IS NULL AND o.channel IS NULL) OR o.channel = :channel)
        """)
    List<CommunicationOptOut> findActiveExact(TenantId tenantId, String value, CommunicationChannel channel);

    @Query("SELECT o FROM CommunicationOptOut o WHERE o.tenantId = :tenantId AND o.revokedAt IS NULL ORDER BY o.createdAt DESC")
    List<CommunicationOptOut> findAllActive(TenantId tenantId);
}
