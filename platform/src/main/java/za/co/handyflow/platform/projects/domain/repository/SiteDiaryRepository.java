package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.SiteDiary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteDiaryRepository extends JpaRepository<SiteDiary, UUID> {

    @Query("SELECT d FROM SiteDiary d WHERE d.projectId = :projectId ORDER BY d.diaryDate DESC")
    List<SiteDiary> findByProject(UUID projectId);

    @Query("SELECT d FROM SiteDiary d WHERE d.projectId = :projectId AND d.diaryDate = :date")
    Optional<SiteDiary> findByProjectAndDate(UUID projectId, LocalDate date);

    @Query("SELECT d FROM SiteDiary d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<SiteDiary> findByTenantAndId(UUID tenantId, UUID id);
}
