// property/domain/repository/LeaseRepository.java

package za.co.handyflow.platform.property.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.property.domain.model.Lease;
import za.co.handyflow.platform.shared.TenantId;

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
}