package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryObligation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Requirement-traceability queries on the SAME entity the existing RegulatoryObligationRepository serves.
 * Kept separate so the proven repository file is not edited.
 */
public interface RegulatoryObligationLinkRepository extends JpaRepository<RegulatoryObligation, UUID> {

    @Query("SELECT o FROM RegulatoryObligation o WHERE o.tenantId = :tenantId AND o.requirementId = :requirementId AND o.deletedAt IS NULL")
    Optional<RegulatoryObligation> findLiveByRequirement(TenantId tenantId, UUID requirementId);

    @Query("SELECT o FROM RegulatoryObligation o WHERE o.tenantId = :tenantId AND o.requirementId IS NOT NULL AND o.deletedAt IS NULL")
    List<RegulatoryObligation> findAllLiveLinked(TenantId tenantId);
}
