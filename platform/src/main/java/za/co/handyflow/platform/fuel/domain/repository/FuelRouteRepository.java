package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelRoute;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelRouteRepository extends JpaRepository<FuelRoute, UUID> {

    @Query("SELECT r FROM FuelRoute r WHERE r.tenantId = :tenantId AND r.routeDate = :date ORDER BY r.createdAt")
    List<FuelRoute> findByDate(TenantId tenantId, LocalDate date);

    @Query("SELECT r FROM FuelRoute r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<FuelRoute> findOne(TenantId tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FuelRoute r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<FuelRoute> findOneForUpdate(TenantId tenantId, UUID id);
}
