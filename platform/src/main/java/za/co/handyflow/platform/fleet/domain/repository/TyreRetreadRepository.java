package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.TyreRetread;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface TyreRetreadRepository extends JpaRepository<TyreRetread, UUID> {

    @Query("SELECT r FROM TyreRetread r WHERE r.tenantId = :tenantId AND r.tyreId = :tyreId ORDER BY r.retreadedAt DESC")
    List<TyreRetread> findByTyre(TenantId tenantId, UUID tyreId);
}