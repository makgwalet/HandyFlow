// fuel/domain/repository/FuelSupplierRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplier;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelSupplierRepository extends JpaRepository<FuelSupplier, UUID> {

    @Query("SELECT s FROM FuelSupplier s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL ORDER BY s.name")
    List<FuelSupplier> findAllActive(TenantId tenantId);

    @Query("SELECT s FROM FuelSupplier s WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<FuelSupplier> findActiveById(TenantId tenantId, UUID id);
}