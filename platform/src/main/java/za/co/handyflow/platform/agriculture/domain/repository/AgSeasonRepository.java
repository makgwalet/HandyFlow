package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgSeason;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/** Farm-scoped, mirrors AgProductionAreaRepository's own shape. */
public interface AgSeasonRepository extends JpaRepository<AgSeason, UUID> {

    @Query("SELECT s FROM AgSeason s WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<AgSeason> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT s FROM AgSeason s WHERE s.tenantId = :tenantId AND s.farmId = :farmId AND s.deletedAt IS NULL ORDER BY s.startDate DESC")
    Page<AgSeason> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    @Query("SELECT s FROM AgSeason s WHERE s.tenantId = :tenantId AND s.farmId = :farmId AND s.status = :status AND s.deletedAt IS NULL ORDER BY s.startDate DESC")
    Page<AgSeason> findByStatusForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable);
}
