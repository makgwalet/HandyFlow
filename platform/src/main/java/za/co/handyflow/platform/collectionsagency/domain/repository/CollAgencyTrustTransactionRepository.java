package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyTrustTransaction;

import java.util.List;
import java.util.UUID;

public interface CollAgencyTrustTransactionRepository extends JpaRepository<CollAgencyTrustTransaction, UUID> {

    @Query("SELECT t FROM CollAgencyTrustTransaction t WHERE t.tenantId = :tenantId AND t.clientId = :clientId ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<CollAgencyTrustTransaction> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT t FROM CollAgencyTrustTransaction t WHERE t.tenantId = :tenantId AND t.debtorAccountId = :debtorAccountId ORDER BY t.transactionDate DESC")
    List<CollAgencyTrustTransaction> findByDebtorAccount(@Param("tenantId") UUID tenantId, @Param("debtorAccountId") UUID debtorAccountId);

    /** Receipts not yet covered by a subsequent remittance — used for the trust reconciliation report. Simple date-ordered replay is left to the service layer rather than modeled as a "settled" flag here, since a receipt is never mutated once recorded (see entity Javadoc). */
    @Query("SELECT t FROM CollAgencyTrustTransaction t WHERE t.tenantId = :tenantId AND t.clientId = :clientId AND t.transactionType = 'RECEIPT' ORDER BY t.transactionDate ASC")
    List<CollAgencyTrustTransaction> findReceiptsByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);
}
