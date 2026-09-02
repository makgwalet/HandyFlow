package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgWeightRecord;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface AgWeightRecordRepository extends JpaRepository<AgWeightRecord, UUID> {

    @Query("SELECT w FROM AgWeightRecord w WHERE w.tenantId = :tenantId AND w.id = :id")
    Optional<AgWeightRecord> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT w FROM AgWeightRecord w WHERE w.tenantId = :tenantId AND w.animalId = :animalId ORDER BY w.recordedDate DESC")
    Page<AgWeightRecord> findByAnimal(TenantId tenantId, UUID animalId, Pageable pageable);

    @Query("SELECT w FROM AgWeightRecord w WHERE w.tenantId = :tenantId AND w.groupId = :groupId ORDER BY w.recordedDate DESC")
    Page<AgWeightRecord> findByGroup(TenantId tenantId, UUID groupId, Pageable pageable);
}
