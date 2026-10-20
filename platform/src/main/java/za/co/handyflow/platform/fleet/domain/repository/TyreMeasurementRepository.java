package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.TyreMeasurement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface TyreMeasurementRepository extends JpaRepository<TyreMeasurement, UUID> {

    /** Newest first; pass a Pageable to cap how many come back. */
    @Query("SELECT m FROM TyreMeasurement m WHERE m.tenantId = :tenantId AND m.tyreId = :tyreId ORDER BY m.measuredAt DESC")
    List<TyreMeasurement> findByTyre(TenantId tenantId, UUID tyreId, Pageable pageable);
}