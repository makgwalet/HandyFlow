// fuel/domain/repository/FuelDeliveryRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FuelDeliveryRepository extends JpaRepository<FuelDelivery, UUID> {

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL ORDER BY d.scheduledAt DESC")
    Page<FuelDelivery> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.status = :status AND d.deletedAt IS NULL ORDER BY d.scheduledAt ASC")
    Page<FuelDelivery> findByStatus(TenantId tenantId, String status, Pageable pageable);

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.id = :id AND d.deletedAt IS NULL")
    Optional<FuelDelivery> findActiveById(TenantId tenantId, UUID id);
}