package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustTransaction;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read-only sums over the trust ledger for reconciliation. Receipts add; every other type subtracts. */
public interface LpTrustLedgerReadRepository extends JpaRepository<LpTrustTransaction, UUID> {

    /** The whole trust account as at the end of {@code asAt}. */
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN t.transactionType = 'RECEIPT' THEN t.amount ELSE -t.amount END), 0)
        FROM LpTrustTransaction t WHERE t.tenantId = :tenantId AND t.transactionDate <= :asAt
        """)
    BigDecimal cashBookAsAt(TenantId tenantId, LocalDate asAt);

    /** Each client's balance as at the end of {@code asAt}. Rows are {@code [clientId, balance]}. */
    @Query("""
        SELECT t.clientId, SUM(CASE WHEN t.transactionType = 'RECEIPT' THEN t.amount ELSE -t.amount END)
        FROM LpTrustTransaction t WHERE t.tenantId = :tenantId AND t.transactionDate <= :asAt
        GROUP BY t.clientId
        """)
    List<Object[]> clientBalancesAsAt(TenantId tenantId, LocalDate asAt);

    /** The balance each client record holds. Rows are {@code [clientId, name, trustBalance]}. */
    @Query("SELECT c.id, c.name, c.trustBalance FROM LpClient c WHERE c.tenantId = :tenantId")
    List<Object[]> storedClientBalances(TenantId tenantId);
}
