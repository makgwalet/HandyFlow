// fuel/domain/repository/DipReadingRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.DipReading;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DipReadingRepository extends JpaRepository<DipReading, UUID> {

    @Query("SELECT d FROM DipReading d WHERE d.tankId = :tankId ORDER BY d.readAt DESC")
    Page<DipReading> findByTank(UUID tankId, Pageable pageable);

    /** Backs the reconciliation report — full (non-paginated) history within a date range, oldest first for a readable report. */
    @Query("SELECT d FROM DipReading d WHERE d.tankId = :tankId AND d.readAt BETWEEN :from AND :to ORDER BY d.readAt ASC")
    List<DipReading> findByTankAndReadAtBetween(UUID tankId, Instant from, Instant to);
}