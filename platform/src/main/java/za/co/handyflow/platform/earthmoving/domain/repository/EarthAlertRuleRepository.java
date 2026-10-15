package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAlertRule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthAlertRuleRepository extends JpaRepository<EarthAlertRule, UUID> {
    @Query("SELECT r FROM EarthAlertRule r WHERE r.tenantId = :tenantId ORDER BY r.alertType")
    List<EarthAlertRule> findAllForTenant(TenantId tenantId);

    @Query("SELECT r FROM EarthAlertRule r WHERE r.tenantId = :tenantId AND r.alertType = :alertType")
    Optional<EarthAlertRule> findByType(TenantId tenantId, String alertType);
}
