// property/domain/repository/UnitRepository.java

package za.co.handyflow.platform.property.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.property.domain.model.Unit;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    @Query("SELECT u FROM Unit u WHERE u.tenantId = :tenantId AND u.deletedAt IS NULL ORDER BY u.unitNumber")
    Page<Unit> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT u FROM Unit u WHERE u.property.id = :propertyId AND u.deletedAt IS NULL ORDER BY u.unitNumber")
    Page<Unit> findByProperty(UUID propertyId, Pageable pageable);

    @Query("SELECT u FROM Unit u WHERE u.tenantId = :tenantId AND u.id = :id AND u.deletedAt IS NULL")
    Optional<Unit> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT u FROM Unit u WHERE u.tenantId = :tenantId AND u.status = :status AND u.deletedAt IS NULL ORDER BY u.unitNumber")
    Page<Unit> findByStatus(TenantId tenantId, String status, Pageable pageable);

    boolean existsByPropertyIdAndUnitNumberAndDeletedAtIsNull(UUID propertyId, String unitNumber);
}