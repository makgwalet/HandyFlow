package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkTimeEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link BkTimeEntry} has no {@code deletedAt} — matches {@code accountant.TimeEntry}'s own bare shape. */
public interface BkTimeEntryRepository extends JpaRepository<BkTimeEntry, UUID> {

    @Query("SELECT t FROM BkTimeEntry t WHERE t.tenantId = :#{#tenantId.value} AND t.id = :id")
    Optional<BkTimeEntry> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM BkTimeEntry t WHERE t.tenantId = :#{#tenantId.value} AND t.clientId = :clientId ORDER BY t.entryDate DESC")
    Page<BkTimeEntry> findByClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT t FROM BkTimeEntry t WHERE t.tenantId = :#{#tenantId.value} AND t.clientId = :clientId AND t.status = 'UNBILLED' ORDER BY t.entryDate")
    List<BkTimeEntry> findUnbilledByClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /** Billing-period candidates for {@code BkBillingService}'s time-and-materials branch. */
    @Query("SELECT t FROM BkTimeEntry t WHERE t.tenantId = :#{#tenantId.value} AND t.clientId = :clientId " +
           "AND t.status = 'UNBILLED' AND t.billable = true AND t.entryDate BETWEEN :periodStart AND :periodEnd ORDER BY t.entryDate")
    List<BkTimeEntry> findUnbilledInRange(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                           @Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);
}
