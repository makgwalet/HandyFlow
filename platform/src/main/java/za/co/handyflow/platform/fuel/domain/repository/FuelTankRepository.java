// fuel/domain/repository/FuelTankRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTankRepository extends JpaRepository<FuelTank, UUID> {

    @Query("SELECT t FROM FuelTank t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL ORDER BY t.name")
    List<FuelTank> findAllActive(TenantId tenantId);

    @Query("SELECT t FROM FuelTank t WHERE t.tenantId = :tenantId AND t.id = :id AND t.deletedAt IS NULL")
    Optional<FuelTank> findActiveById(TenantId tenantId, UUID id);
}