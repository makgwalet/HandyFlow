// property/domain/repository/PropertyRepository.java

package za.co.handyflow.platform.property.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.property.domain.model.Property;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    @Query("SELECT p FROM Property p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL ORDER BY p.name")
    Page<Property> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.units WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<Property> findActiveByIdWithUnits(TenantId tenantId, UUID id);

    @Query("SELECT p FROM Property p WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<Property> findActiveById(TenantId tenantId, UUID id);
}