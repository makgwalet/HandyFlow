package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpCourtCalendarEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpCourtCalendarEntryRepository extends JpaRepository<LpCourtCalendarEntry, UUID> {

    @Query("SELECT e FROM LpCourtCalendarEntry e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<LpCourtCalendarEntry> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT e FROM LpCourtCalendarEntry e
        WHERE e.tenantId = :tenantId AND e.entryDate >= :from AND e.entryDate <= :to
        ORDER BY e.entryDate ASC
        """)
    List<LpCourtCalendarEntry> findInRange(TenantId tenantId, LocalDate from, LocalDate to);

    @Query("SELECT e FROM LpCourtCalendarEntry e WHERE e.tenantId = :tenantId AND e.entryDate = :date AND e.kind = :kind")
    Optional<LpCourtCalendarEntry> findOnDate(TenantId tenantId, LocalDate date, String kind);
}
