package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoiDeclarationRepository extends JpaRepository<CoiDeclaration, UUID> {

    @Query("SELECT x FROM CoiDeclaration x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<CoiDeclaration> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT x FROM CoiDeclaration x WHERE x.tenantId = :tenantId
        AND (:declarantId IS NULL OR x.declarantId = :declarantId)
        AND (:status IS NULL OR x.status = :status)
        ORDER BY x.declaredOn DESC
        """)
    List<CoiDeclaration> search(TenantId tenantId, UUID declarantId, DeclarationStatus status);

    /** Cross-tenant — daily sweep: declarations awaiting review, and cleared or mitigated ones whose review date has passed. */
    @Query("""
        SELECT x FROM CoiDeclaration x WHERE x.status IN (za.co.handyflow.platform.legalcompliance.domain.model.DeclarationStatus.DECLARED, za.co.handyflow.platform.legalcompliance.domain.model.DeclarationStatus.UNDER_REVIEW)
        OR (x.status IN (za.co.handyflow.platform.legalcompliance.domain.model.DeclarationStatus.CLEARED, za.co.handyflow.platform.legalcompliance.domain.model.DeclarationStatus.MITIGATED) AND x.nextReviewDue <= :date)
        """)
    List<CoiDeclaration> findNeedingAttentionAcrossTenants(LocalDate date);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
