package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelEnforcementObservation;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FuelEnforcementObservationRepository extends JpaRepository<FuelEnforcementObservation, UUID> {

    @Query("SELECT COUNT(o) FROM FuelEnforcementObservation o WHERE o.tenantId = :tenantId AND o.control = :control AND o.createdAt >= :since")
    long countSince(TenantId tenantId, String control, Instant since);

    @Query("SELECT o FROM FuelEnforcementObservation o WHERE o.tenantId = :tenantId AND o.control = :control AND o.createdAt >= :since ORDER BY o.createdAt DESC")
    List<FuelEnforcementObservation> recent(TenantId tenantId, String control, Instant since, org.springframework.data.domain.Pageable page);
}
