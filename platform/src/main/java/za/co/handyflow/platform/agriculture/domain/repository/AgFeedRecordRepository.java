package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgFeedRecord;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface AgFeedRecordRepository extends JpaRepository<AgFeedRecord, UUID> {

    @Query("SELECT f FROM AgFeedRecord f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<AgFeedRecord> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT f FROM AgFeedRecord f WHERE f.tenantId = :tenantId AND f.animalId = :animalId ORDER BY f.feedDate DESC")
    Page<AgFeedRecord> findByAnimal(TenantId tenantId, UUID animalId, Pageable pageable);

    @Query("SELECT f FROM AgFeedRecord f WHERE f.tenantId = :tenantId AND f.groupId = :groupId ORDER BY f.feedDate DESC")
    Page<AgFeedRecord> findByGroup(TenantId tenantId, UUID groupId, Pageable pageable);

    // Backs AgCostReportingService — COALESCE so an animal/group with zero
    // feed records returns 0, not null, mirroring
    // VehicleServiceRepository.sumCostByVehicle().
    @Query("SELECT COALESCE(SUM(f.totalCost), 0) FROM AgFeedRecord f WHERE f.tenantId = :tenantId AND f.animalId = :animalId")
    BigDecimal sumTotalCostByAnimal(TenantId tenantId, UUID animalId);

    @Query("SELECT COALESCE(SUM(f.totalCost), 0) FROM AgFeedRecord f WHERE f.tenantId = :tenantId AND f.groupId = :groupId")
    BigDecimal sumTotalCostByGroup(TenantId tenantId, UUID groupId);
}
