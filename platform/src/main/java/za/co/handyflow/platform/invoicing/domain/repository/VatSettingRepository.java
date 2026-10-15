package za.co.handyflow.platform.invoicing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.invoicing.domain.model.VatSetting;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VatSettingRepository extends JpaRepository<VatSetting, UUID> {

    @Query("SELECT v FROM VatSetting v WHERE v.tenantId = :tenantId AND v.cancelledAt IS NULL ORDER BY v.effectiveFrom DESC")
    List<VatSetting> findLive(TenantId tenantId);

    @Query("SELECT v FROM VatSetting v WHERE v.tenantId = :tenantId AND v.id = :id")
    Optional<VatSetting> findOwned(TenantId tenantId, UUID id);
}
