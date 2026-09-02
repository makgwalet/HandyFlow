package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * One {@link LpProfile} row per tenant — a plain tenant-scoped lookup,
 * no soft delete (the entity has none), mirroring {@code FuelTankRepository}'s
 * shape for a plain non-soft-deleted entity.
 */
public interface LpProfileRepository extends JpaRepository<LpProfile, UUID> {

    @Query("SELECT p FROM LpProfile p WHERE p.tenantId = :tenantId")
    Optional<LpProfile> findByTenantId(TenantId tenantId);
}
