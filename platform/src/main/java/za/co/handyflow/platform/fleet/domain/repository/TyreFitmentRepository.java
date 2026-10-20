package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.TyreFitment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TyreFitmentRepository extends JpaRepository<TyreFitment, UUID> {

    @Query("SELECT f FROM TyreFitment f WHERE f.tenantId = :tenantId AND f.tyreId = :tyreId ORDER BY f.fittedAt DESC")
    List<TyreFitment> findByTyre(TenantId tenantId, UUID tyreId);

    @Query("SELECT f FROM TyreFitment f WHERE f.tenantId = :tenantId AND f.tyreId = :tyreId AND f.removedAt IS NULL")
    Optional<TyreFitment> findOpenByTyre(TenantId tenantId, UUID tyreId);
}