package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgMortalityRecord;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AgMortalityRecordRepository extends JpaRepository<AgMortalityRecord, UUID> {

    @Query("SELECT m FROM AgMortalityRecord m WHERE m.tenantId = :tenantId AND m.id = :id")
    Optional<AgMortalityRecord> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT m FROM AgMortalityRecord m WHERE m.tenantId = :tenantId AND m.animalId = :animalId ORDER BY m.mortalityDate DESC")
    Page<AgMortalityRecord> findByAnimal(TenantId tenantId, UUID animalId, Pageable pageable);

    @Query("SELECT m FROM AgMortalityRecord m WHERE m.tenantId = :tenantId AND m.groupId = :groupId ORDER BY m.mortalityDate DESC")
    Page<AgMortalityRecord> findByGroup(TenantId tenantId, UUID groupId, Pageable pageable);

    // Trends: [mortalityDate, countLost, estimatedValueLoss, animalId, groupId].
    @Query("SELECT m.mortalityDate, m.countLost, m.estimatedValueLoss, m.animalId, m.groupId FROM AgMortalityRecord m WHERE m.tenantId = :tenantId AND m.mortalityDate >= :startDate AND m.mortalityDate <= :endDate")
    List<Object[]> findBetween(TenantId tenantId, LocalDate startDate, LocalDate endDate);
}
