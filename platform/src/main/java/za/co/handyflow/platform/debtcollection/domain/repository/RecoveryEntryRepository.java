package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.RecoveryEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryEntryRepository extends JpaRepository<RecoveryEntry, UUID> {

    @Query("SELECT e FROM RecoveryEntry e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<RecoveryEntry> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT e FROM RecoveryEntry e WHERE e.tenantId = :tenantId AND e.caseId = :caseId ORDER BY e.effectiveDate DESC, e.createdAt DESC")
    List<RecoveryEntry> findByCaseId(TenantId tenantId, UUID caseId);

    /** Idempotency for payments arriving with a bank/gateway reference. */
    @Query("SELECT e FROM RecoveryEntry e WHERE e.tenantId = :tenantId AND e.entryType = 'PAYMENT' AND e.externalReference = :ref")
    Optional<RecoveryEntry> findPaymentByExternalReference(TenantId tenantId, String ref);

    @Query("SELECT e FROM RecoveryEntry e WHERE e.tenantId = :tenantId AND e.reversalOfId = :paymentId")
    Optional<RecoveryEntry> findReversalOf(TenantId tenantId, UUID paymentId);

    @Query("SELECT e FROM RecoveryEntry e WHERE e.tenantId = :tenantId AND e.effectiveDate BETWEEN :from AND :to ORDER BY e.effectiveDate")
    List<RecoveryEntry> findBetween(TenantId tenantId, LocalDate from, LocalDate to);
}
