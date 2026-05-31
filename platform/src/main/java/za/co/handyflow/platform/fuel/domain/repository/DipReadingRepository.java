// fuel/domain/repository/DipReadingRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.DipReading;

import java.util.UUID;

public interface DipReadingRepository extends JpaRepository<DipReading, UUID> {

    @Query("SELECT d FROM DipReading d WHERE d.tankId = :tankId ORDER BY d.readAt DESC")
    Page<DipReading> findByTank(UUID tankId, Pageable pageable);
}