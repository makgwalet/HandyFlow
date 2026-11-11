package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceEvidence;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelComplianceEvidenceRepository extends JpaRepository<FuelComplianceEvidence, UUID> {

    @Query("SELECT e FROM FuelComplianceEvidence e WHERE e.tenantId = :tenantId AND e.itemId = :itemId ORDER BY e.capturedAt DESC")
    List<FuelComplianceEvidence> findByItem(TenantId tenantId, UUID itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM FuelComplianceEvidence e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<FuelComplianceEvidence> findByIdForUpdate(TenantId tenantId, UUID id);

    /** Rows of [itemId, active evidence count] for every item that has any. */
    @Query("SELECT e.itemId, COUNT(e) FROM FuelComplianceEvidence e WHERE e.tenantId = :tenantId AND e.status = 'ACTIVE' GROUP BY e.itemId")
    List<Object[]> countActiveByItem(TenantId tenantId);

    @Query("SELECT COUNT(e) FROM FuelComplianceEvidence e WHERE e.tenantId = :tenantId AND e.itemId = :itemId AND e.status = 'ACTIVE'")
    long countActive(TenantId tenantId, UUID itemId);
}
