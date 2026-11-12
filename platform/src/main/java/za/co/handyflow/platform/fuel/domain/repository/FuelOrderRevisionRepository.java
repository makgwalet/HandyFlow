package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelOrderRevision;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelOrderRevisionRepository extends JpaRepository<FuelOrderRevision, UUID> {
    @Query("SELECT r FROM FuelOrderRevision r WHERE r.tenantId = :tenantId AND r.orderId = :orderId ORDER BY r.revisionNo")
    List<FuelOrderRevision> findByOrder(TenantId tenantId, UUID orderId);

    @Query("SELECT COALESCE(MAX(r.revisionNo), 0) FROM FuelOrderRevision r WHERE r.tenantId = :tenantId AND r.orderId = :orderId")
    int latestNo(TenantId tenantId, UUID orderId);
}
