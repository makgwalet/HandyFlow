package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpHearing;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpHearingRepository extends JpaRepository<LpHearing, UUID> {

    @Query("SELECT h FROM LpHearing h WHERE h.tenantId = :tenantId AND h.id = :id")
    Optional<LpHearing> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT h FROM LpHearing h WHERE h.tenantId = :tenantId AND h.caseId = :caseId ORDER BY h.hearingDate DESC, h.hearingTime DESC")
    List<LpHearing> findForCase(TenantId tenantId, UUID caseId);

    @Query("SELECT COUNT(h) FROM LpHearing h WHERE h.tenantId = :tenantId AND h.caseId = :caseId AND h.status = 'SCHEDULED'")
    long countScheduled(TenantId tenantId, UUID caseId);

    /** Scheduled hearings in a date range across the firm (the court roll), leaving out walled matters and clients. Rows are {@code [LpHearing, LpLitigationCase]}. */
    @Query("SELECT h, c FROM LpHearing h, LpLitigationCase c WHERE h.tenantId = :tenantId AND c.tenantId = :tenantId AND c.id = h.caseId "
            + "AND h.status = 'SCHEDULED' AND h.hearingDate >= :from AND h.hearingDate <= :to "
            + "AND h.matterId NOT IN :barredMatterIds AND c.clientId NOT IN :barredClientIds ORDER BY h.hearingDate ASC, h.hearingTime ASC")
    List<Object[]> findRollExcluding(TenantId tenantId, LocalDate from, LocalDate to, List<UUID> barredMatterIds, List<UUID> barredClientIds);
}
