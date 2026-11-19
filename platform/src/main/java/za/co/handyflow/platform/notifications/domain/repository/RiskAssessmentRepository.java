package za.co.handyflow.platform.notifications.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

    @Query("SELECT a FROM RiskAssessment a WHERE a.tenantId = :tenantId AND a.riskId = :riskId AND a.inForce = TRUE")
    Optional<RiskAssessment> findCurrent(TenantId tenantId, UUID riskId);

    @Query("SELECT a FROM RiskAssessment a WHERE a.tenantId = :tenantId AND a.inForce = TRUE")
    List<RiskAssessment> findAllCurrent(TenantId tenantId);

    @Query("SELECT a FROM RiskAssessment a WHERE a.tenantId = :tenantId AND a.riskId = :riskId ORDER BY a.assessedAt DESC")
    List<RiskAssessment> history(TenantId tenantId, UUID riskId);
}
