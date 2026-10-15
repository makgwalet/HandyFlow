package za.co.handyflow.platform.internalaudit.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditInvestigation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditInvestigationRepository extends JpaRepository<AuditInvestigation, UUID> {
    Optional<AuditInvestigation> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Locks the case row so two people writing at once cannot both take the same place in the chain. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AuditInvestigation i where i.tenantId = :tenantId and i.id = :id")
    Optional<AuditInvestigation> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    long countByTenantId(UUID tenantId);
    boolean existsByTenantIdAndNumber(UUID tenantId, String number);

    @Query("select i from AuditInvestigation i where i.tenantId = :tenantId and i.id in :ids order by i.openedAt desc")
    List<AuditInvestigation> findAllByIds(@Param("tenantId") UUID tenantId, @Param("ids") List<UUID> ids);
}
