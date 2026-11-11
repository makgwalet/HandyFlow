package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceRenewal;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelComplianceRenewalRepository extends JpaRepository<FuelComplianceRenewal, UUID> {

    @Query("SELECT r FROM FuelComplianceRenewal r WHERE r.tenantId = :tenantId AND r.itemId = :itemId ORDER BY r.renewedAt DESC")
    List<FuelComplianceRenewal> findByItem(TenantId tenantId, UUID itemId);
}
