package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgBreedingRecord;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AgBreedingRecordRepository extends JpaRepository<AgBreedingRecord, UUID> {

    @Query("SELECT b FROM AgBreedingRecord b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<AgBreedingRecord> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT b FROM AgBreedingRecord b WHERE b.tenantId = :tenantId AND b.animalId = :animalId ORDER BY b.matingDate DESC")
    Page<AgBreedingRecord> findByAnimal(TenantId tenantId, UUID animalId, Pageable pageable);

    @Query("SELECT b FROM AgBreedingRecord b WHERE b.tenantId = :tenantId AND b.groupId = :groupId ORDER BY b.matingDate DESC")
    Page<AgBreedingRecord> findByGroup(TenantId tenantId, UUID groupId, Pageable pageable);

    // Trends: births that have been recorded: [actualBirthDate, offspringCount, animalId, groupId].
    @Query("SELECT b.actualBirthDate, b.offspringCount, b.animalId, b.groupId FROM AgBreedingRecord b WHERE b.tenantId = :tenantId AND b.actualBirthDate IS NOT NULL AND b.actualBirthDate >= :startDate AND b.actualBirthDate <= :endDate")
    List<Object[]> findBirthsBetween(TenantId tenantId, LocalDate startDate, LocalDate endDate);
}
