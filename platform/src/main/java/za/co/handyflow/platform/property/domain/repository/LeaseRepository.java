// property/domain/repository/LeaseRepository.java

package za.co.handyflow.platform.property.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.property.domain.model.Lease;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaseRepository extends JpaRepository<Lease, UUID> {

    @Query("SELECT l FROM Lease l WHERE l.tenantId = :tenantId AND l.deletedAt IS NULL ORDER BY l.startDate DESC")
    Page<Lease> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT l FROM Lease l WHERE l.tenantId = :tenantId AND l.id = :id AND l.deletedAt IS NULL")
    Optional<Lease> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT l FROM Lease l WHERE l.unitId = :unitId AND l.status = 'ACTIVE' AND l.deletedAt IS NULL")
    Optional<Lease> findActiveLease(UUID unitId);

    @Query("SELECT l FROM Lease l WHERE l.tenantId = :tenantId AND l.status = :status AND l.deletedAt IS NULL ORDER BY l.startDate DESC")
    Page<Lease> findByStatus(TenantId tenantId, String status, Pageable pageable);

    // NEW: backs the lease-expiry scheduler. Deliberately cross-tenant — no
    // tenant_id filter — since the scheduler runs once for the whole
    // platform, not per-tenant. Returns real Lease entities rather than raw
    // UUIDs specifically so each one already carries its own correctly-typed
    // TenantId via its embedded field; that avoids needing to reconstruct a
    // TenantId from a raw UUID afterward (no confirmed factory for that
    // exists anywhere in what's been available to check against this
    // session — safer to sidestep the need entirely than assume one).
    @Query("""
            SELECT l FROM Lease l
            WHERE l.deletedAt IS NULL AND l.status = 'ACTIVE'
              AND l.endDate IS NOT NULL AND l.endDate <= :cutoff
            """)
    List<Lease> findAllActiveExpiringBy(LocalDate cutoff);
}