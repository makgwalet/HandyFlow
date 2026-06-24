package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.projects.domain.model.TimeEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectTimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    @Query("SELECT t FROM TimeEntry t WHERE t.projectId = :projectId ORDER BY t.entryDate DESC")
    List<TimeEntry> findByProject(UUID projectId);

    @Query("SELECT t FROM TimeEntry t WHERE t.tenantId = :tenantId AND t.userId = :userId AND t.entryDate BETWEEN :from AND :to ORDER BY t.entryDate DESC")
    List<TimeEntry> findByUserAndPeriod(UUID tenantId, UUID userId, LocalDate from, LocalDate to);

    @Query("SELECT t FROM TimeEntry t WHERE t.tenantId = :tenantId AND t.status = 'SUBMITTED' ORDER BY t.entryDate")
    List<TimeEntry> findPendingApproval(UUID tenantId);

    @Query("SELECT COALESCE(SUM(t.hours), 0) FROM TimeEntry t WHERE t.projectId = :projectId AND t.status != 'REJECTED'")
    java.math.BigDecimal sumHoursByProject(UUID projectId);

    @Query("SELECT t FROM TimeEntry t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<TimeEntry> findByTenantAndId(UUID tenantId, UUID id);
}
