package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkPeriod;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/** {@link BkPeriod} has no {@code deletedAt} — a period is never deleted, only closed/reopened. */
public interface BkPeriodRepository extends JpaRepository<BkPeriod, UUID> {

    @Query("SELECT p FROM BkPeriod p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<BkPeriod> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM BkPeriod p WHERE p.tenantId = :#{#tenantId.value} AND p.clientId = :clientId ORDER BY p.periodYear DESC, p.periodMonth DESC")
    Page<BkPeriod> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    /** Resolve-or-create anchor for {@code BkPeriodService.resolveOrCreate}. */
    @Query("SELECT p FROM BkPeriod p WHERE p.tenantId = :#{#tenantId.value} AND p.clientId = :clientId " +
           "AND p.periodYear = :year AND p.periodMonth = :month")
    Optional<BkPeriod> findByClientAndYearMonth(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                                 @Param("year") int year, @Param("month") int month);
}
