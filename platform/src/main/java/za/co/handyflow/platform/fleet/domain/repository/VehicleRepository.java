// fleet/domain/repository/VehicleRepository.java

package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    @Query("SELECT v FROM Vehicle v WHERE v.tenantId = :tenantId AND v.deletedAt IS NULL ORDER BY v.make, v.model")
    Page<Vehicle> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT v FROM Vehicle v WHERE v.tenantId = :tenantId AND v.id = :id AND v.deletedAt IS NULL")
    Optional<Vehicle> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT v FROM Vehicle v WHERE v.tenantId = :tenantId AND v.status = :status AND v.deletedAt IS NULL ORDER BY v.registration")
    Page<Vehicle> findByStatus(TenantId tenantId, String status, Pageable pageable);

    boolean existsByTenantIdAndRegistrationAndDeletedAtIsNull(TenantId tenantId, String registration);
}