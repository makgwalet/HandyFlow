package za.co.handyflow.platform.pos.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.pos.domain.model.PosSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface PosSettingsRepository extends JpaRepository<PosSettings, UUID> {
    @Query("SELECT s FROM PosSettings s WHERE s.tenantId = :tenantId")
    Optional<PosSettings> findByTenantId(TenantId tenantId);
}