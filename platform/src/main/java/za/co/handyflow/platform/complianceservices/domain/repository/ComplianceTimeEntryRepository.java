package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceTimeEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceTimeEntryRepository extends JpaRepository<ComplianceTimeEntry, UUID> {

    @Query("SELECT t FROM ComplianceTimeEntry t WHERE t.tenantId.value = :#{#tenantId.value} AND t.id = :id")
    Optional<ComplianceTimeEntry> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM ComplianceTimeEntry t WHERE t.tenantId.value = :#{#tenantId.value} AND t.userId = :userId AND t.workDate BETWEEN :from AND :to ORDER BY t.workDate DESC, t.createdAt DESC")
    List<ComplianceTimeEntry> findForUser(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT t FROM ComplianceTimeEntry t WHERE t.tenantId.value = :#{#tenantId.value} AND t.clientId = :clientId AND t.workDate BETWEEN :from AND :to ORDER BY t.workDate DESC, t.createdAt DESC")
    List<ComplianceTimeEntry> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Minutes this person has already recorded for the day, not counting voided entries. */
    @Query("SELECT COALESCE(SUM(t.minutes), 0) FROM ComplianceTimeEntry t WHERE t.tenantId.value = :#{#tenantId.value} AND t.userId = :userId AND t.workDate = :day AND t.status = 'ACTIVE'")
    long minutesOnDay(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId, @Param("day") LocalDate day);

    /** Every active entry in the period, for the whole practice. */
    @Query("SELECT t FROM ComplianceTimeEntry t WHERE t.tenantId.value = :#{#tenantId.value} AND t.status = 'ACTIVE' AND t.workDate BETWEEN :from AND :to")
    List<ComplianceTimeEntry> findActiveBetween(@Param("tenantId") TenantId tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
