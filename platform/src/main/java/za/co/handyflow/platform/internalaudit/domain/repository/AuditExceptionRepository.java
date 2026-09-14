package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditExceptionRepository extends JpaRepository<AuditException, UUID> {

    @Query("SELECT e FROM AuditException e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<AuditException> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT e FROM AuditException e WHERE e.tenantId = :tenantId AND e.auditTestId = :testId")
    List<AuditException> findByTest(@Param("tenantId") UUID tenantId, @Param("testId") UUID testId);

    // Backs the engagement-wide exceptions view (Phase 3 doesn't yet
    // have a findings rollup, that's Phase 4) — so this is how a
    // reviewer sees every open exception across an engagement's
    // sampling plans today.
    @Query("""
        SELECT e FROM AuditException e
        WHERE e.tenantId = :tenantId
        AND e.auditTestId IN (
            SELECT t.id FROM AuditTest t WHERE t.sampleItemId IN (
                SELECT s.id FROM SampleItem s WHERE s.samplingPlanId IN (
                    SELECT p.id FROM SamplingPlan p WHERE p.engagementId = :engagementId
                )
            )
        )
        ORDER BY e.raisedAt DESC
        """)
    List<AuditException> findByEngagement(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);
}
