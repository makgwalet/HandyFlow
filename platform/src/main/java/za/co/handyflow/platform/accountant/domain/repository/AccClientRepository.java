package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccClient;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccClientRepository extends JpaRepository<AccClient, UUID> {

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.deletedAt IS NULL
        ORDER BY c.tradingName ASC
    """)
    Page<AccClient> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.id = :id
          AND c.deletedAt IS NULL
    """)
    Optional<AccClient> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.riskRating = :risk
          AND c.deletedAt IS NULL
        ORDER BY c.tradingName ASC
    """)
    List<AccClient> findByRisk(@Param("tenantId") TenantId tenantId, @Param("risk") String risk);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.ficaCompleted = false
          AND c.deletedAt IS NULL
        ORDER BY c.tradingName ASC
    """)
    List<AccClient> findFicaIncomplete(@Param("tenantId") TenantId tenantId);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.tcsPinExpiry IS NOT NULL
          AND c.tcsPinExpiry < :cutoff
          AND c.deletedAt IS NULL
    """)
    List<AccClient> findWithExpiredTcsPin(@Param("tenantId") TenantId tenantId,
                                          @Param("cutoff") LocalDate cutoff);

    /**
     * NEW: closes the audit's "TCS PIN expiry reminders" gap. Global
     * (no tenant filter) — matches TaxDeadlineRepository.
     * findPendingReminder30/7/1()'s exact pattern, since the daily
     * scheduler processes every tenant's clients in one query, then
     * resolves each client's own tenant to find the right firm email to
     * notify (same structure as AccountantScheduler.sendReminders()
     * already uses for tax deadlines).
     */
    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.deletedAt IS NULL
          AND c.tcsPinExpiry = :targetDate
          AND c.tcsPinReminder30Sent = false
    """)
    List<AccClient> findTcsPinPendingReminder30(@Param("targetDate") LocalDate targetDate);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.deletedAt IS NULL
          AND c.tcsPinExpiry = :targetDate
          AND c.tcsPinReminder7Sent = false
    """)
    List<AccClient> findTcsPinPendingReminder7(@Param("targetDate") LocalDate targetDate);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.deletedAt IS NULL
          AND c.tcsPinExpiry = :targetDate
          AND c.tcsPinReminder1Sent = false
    """)
    List<AccClient> findTcsPinPendingReminder1(@Param("targetDate") LocalDate targetDate);
}