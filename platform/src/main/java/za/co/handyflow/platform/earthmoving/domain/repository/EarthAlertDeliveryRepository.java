package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAlertDelivery;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface EarthAlertDeliveryRepository extends JpaRepository<EarthAlertDelivery, UUID> {
    @Query("SELECT d FROM EarthAlertDelivery d WHERE d.tenantId = :tenantId AND d.alertId IN :alertIds ORDER BY d.sentAt")
    List<EarthAlertDelivery> findForAlerts(TenantId tenantId, java.util.Collection<UUID> alertIds);
}
