package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.RiskAssessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

    @Query("SELECT r FROM RiskAssessment r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<RiskAssessment> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM RiskAssessment r WHERE r.tenantId = :tenantId AND r.universeEntryId = :universeEntryId ORDER BY r.assessedAt DESC")
    List<RiskAssessment> findByUniverseEntry(@Param("tenantId") UUID tenantId, @Param("universeEntryId") UUID universeEntryId);

    // Most recent assessment per universe entry — what the Annual Plan
    // screen uses to show current risk without the full history.
    @Query("SELECT r FROM RiskAssessment r WHERE r.tenantId = :tenantId AND r.universeEntryId = :universeEntryId ORDER BY r.assessedAt DESC LIMIT 1")
    Optional<RiskAssessment> findLatestForUniverseEntry(@Param("tenantId") UUID tenantId, @Param("universeEntryId") UUID universeEntryId);
}
