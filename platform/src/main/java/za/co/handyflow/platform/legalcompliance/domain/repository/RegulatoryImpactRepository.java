package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ImpactStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryImpact;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryImpactRepository extends JpaRepository<RegulatoryImpact, UUID> {

    @Query("SELECT i FROM RegulatoryImpact i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<RegulatoryImpact> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT i FROM RegulatoryImpact i WHERE i.tenantId = :tenantId AND i.changeId = :changeId ORDER BY i.reference")
    List<RegulatoryImpact> findByChange(TenantId tenantId, UUID changeId);

    @Query("SELECT i FROM RegulatoryImpact i WHERE i.tenantId = :tenantId AND (:status IS NULL OR i.status = :status) ORDER BY i.dueDate, i.reference")
    List<RegulatoryImpact> findByStatus(TenantId tenantId, ImpactStatus status);

    @Query("SELECT count(i) FROM RegulatoryImpact i WHERE i.tenantId = :tenantId AND i.changeId = :changeId AND i.status = za.co.handyflow.platform.legalcompliance.domain.model.ImpactStatus.PENDING")
    long countPendingInChange(TenantId tenantId, UUID changeId);

    @Query("SELECT i FROM RegulatoryImpact i WHERE i.tenantId = :tenantId AND i.status = za.co.handyflow.platform.legalcompliance.domain.model.ImpactStatus.PENDING AND i.dueDate BETWEEN :from AND :to")
    List<RegulatoryImpact> findPendingDueWithin(TenantId tenantId, LocalDate from, LocalDate to);

    /** Cross-tenant — used only by the daily change-impact sweep. */
    @Query("SELECT i FROM RegulatoryImpact i WHERE i.status = za.co.handyflow.platform.legalcompliance.domain.model.ImpactStatus.PENDING AND i.dueDate <= :cutoff")
    List<RegulatoryImpact> findPendingDueByAcrossTenants(LocalDate cutoff);
}
