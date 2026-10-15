package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAlertRecipient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface EarthAlertRecipientRepository extends JpaRepository<EarthAlertRecipient, UUID> {
    @Query("SELECT r FROM EarthAlertRecipient r WHERE r.tenantId = :tenantId AND r.ruleId = :ruleId ORDER BY r.level")
    List<EarthAlertRecipient> findForRule(TenantId tenantId, UUID ruleId);

    @Modifying
    @Query("DELETE FROM EarthAlertRecipient r WHERE r.tenantId = :tenantId AND r.ruleId = :ruleId")
    void deleteForRule(TenantId tenantId, UUID ruleId);
}
