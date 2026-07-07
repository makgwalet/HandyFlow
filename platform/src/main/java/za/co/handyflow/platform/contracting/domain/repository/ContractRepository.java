package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    @Query("""
        SELECT c FROM Contract c WHERE c.tenantId = :#{#tenantId.value}
        AND c.deletedAt IS NULL
        AND (:status IS NULL OR c.status = :status)
        AND (:type IS NULL OR c.contractType = :type)
        ORDER BY c.createdAt DESC
        """)
    Page<Contract> findAllActive(TenantId tenantId, String status,
                                 String type, Pageable pageable);

    @Query("SELECT c FROM Contract c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.deletedAt IS NULL")
    Optional<Contract> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT c FROM Contract c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'SIGNED' AND c.endDate <= :today AND c.deletedAt IS NULL")
    List<Contract> findExpired(TenantId tenantId, LocalDate today);

    @Query("SELECT c FROM Contract c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'SIGNED' AND c.endDate = :alertDate AND c.deletedAt IS NULL")
    List<Contract> findExpiringOn(TenantId tenantId, LocalDate alertDate);

    @Query("SELECT COUNT(c) FROM Contract c WHERE c.tenantId = :#{#tenantId.value}")
    long countByTenant(TenantId tenantId);

    // NEW: replaces ContractExpiryScheduler's findAllTenantIds() previously
    // implemented as findAll().stream().map(Contract::getTenantId) — that
    // loaded every Contract row across every tenant (full TEXT body column
    // included) into memory just to extract a Set<UUID>. This is the actual
    // lightweight version of that query — a single-column DISTINCT, not a
    // full entity load.
    @Query("SELECT DISTINCT c.tenantId FROM Contract c WHERE c.deletedAt IS NULL")
    List<UUID> findDistinctActiveTenantIds();

    // NEW: replaces the exact-date findExpiringOn() as the basis for renewal
    // reminders. Range-based (endDate between today and today+30) rather than
    // an exact match on a single future date, so a scheduler run that was
    // missed on the exact day a threshold fell due can still catch up on the
    // next run — see Contract.isReminderSent()/markReminderSent() for the
    // idempotency half of this fix.
    @Query("SELECT c FROM Contract c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'SIGNED' " +
            "AND c.endDate IS NOT NULL AND c.endDate > :today AND c.endDate <= :cutoff AND c.deletedAt IS NULL")
    List<Contract> findSignedExpiringWithin(TenantId tenantId, LocalDate today, LocalDate cutoff);
}
