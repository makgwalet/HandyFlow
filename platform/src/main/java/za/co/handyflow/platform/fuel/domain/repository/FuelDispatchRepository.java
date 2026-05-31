// fuel/domain/repository/FuelDispatchRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDispatch;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface FuelDispatchRepository extends JpaRepository<FuelDispatch, UUID> {

    @Query("SELECT d FROM FuelDispatch d WHERE d.tankId = :tankId AND d.deletedAt IS NULL ORDER BY d.dispatchedAt DESC")
    Page<FuelDispatch> findByTank(UUID tankId, Pageable pageable);

    @Query("SELECT d FROM FuelDispatch d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL ORDER BY d.dispatchedAt DESC")
    Page<FuelDispatch> findAllActive(TenantId tenantId, Pageable pageable);
}