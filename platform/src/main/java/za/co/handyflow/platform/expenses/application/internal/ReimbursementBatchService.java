package za.co.handyflow.platform.expenses.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.expenses.application.internal.ExpensePolicyRules.BatchClaim;
import za.co.handyflow.platform.expenses.dto.ExpensePolicyDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Pays approved claims out together (finance F18). A batch is made from approved claims and paid in one go: each claim is
 * reimbursed through the same path as a single payout (so the self-payment check, the bank account and the ledger posting
 * all apply), under one reference. If any claim cannot be paid, none is.
 */
@Service
@RequiredArgsConstructor
public class ReimbursementBatchService {

    private final JdbcTemplate jdbc;
    private final ExpensesService expenses;
    private final AccountingFacade accountingFacade;

    /** Approved claims not yet paid, grouped by employee. */
    @Transactional(readOnly = true)
    public List<OwedToEmployee> owed(TenantId tenantId) {
        Map<String, List<OwedClaim>> byEmployee = new LinkedHashMap<>();
        jdbc.query("""
                SELECT c.employee_name, c.id, c.claim_number, c.claim_date, c.category, c.amount,
                       EXISTS (SELECT 1 FROM expense_reimbursement_batch_items i JOIN expense_reimbursement_batches b ON b.id = i.batch_id
                                WHERE i.claim_id = c.id AND b.status = 'DRAFT')
                  FROM expense_claims c WHERE c.tenant_id = ? AND c.status = 'APPROVED' ORDER BY c.employee_name, c.claim_date""",
                rs -> {
                    byEmployee.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(new OwedClaim(rs.getObject(2, UUID.class),
                            rs.getString(3), rs.getDate(4).toLocalDate(), rs.getString(5), rs.getBigDecimal(6), rs.getBoolean(7)));
                }, tenantId.getValue());
        return byEmployee.entrySet().stream().map(e -> new OwedToEmployee(e.getKey(),
                e.getValue().stream().map(OwedClaim::amount).reduce(BigDecimal.ZERO, BigDecimal::add), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> list(TenantId tenantId) {
        return jdbc.query("SELECT id FROM expense_reimbursement_batches WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 50",
                (rs, n) -> rs.getObject(1, UUID.class), tenantId.getValue()).stream().map(id -> get(tenantId, id)).toList();
    }

    @Transactional(readOnly = true)
    public BatchResponse get(TenantId tenantId, UUID id) {
        BatchResponse head = jdbc.query("""
                SELECT id, batch_number, status, total_amount, bank_account_id, reference, created_at, paid_at
                  FROM expense_reimbursement_batches WHERE id = ? AND tenant_id = ?""",
                (rs, n) -> new BatchResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                        rs.getObject(5, UUID.class), rs.getString(6), rs.getTimestamp(7).toInstant(),
                        rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), List.of()), id, tenantId.getValue())
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("ReimbursementBatch", id.toString()));
        List<BatchItem> items = jdbc.query("""
                SELECT claim_id, claim_number, employee_name, amount FROM expense_reimbursement_batch_items
                 WHERE batch_id = ? ORDER BY employee_name, claim_number""",
                (rs, n) -> new BatchItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)), id);
        return new BatchResponse(head.id(), head.batchNumber(), head.status(), head.totalAmount(), head.bankAccountId(), head.reference(),
                head.createdAt(), head.paidAt(), items);
    }

    @Transactional
    public BatchResponse create(TenantId tenantId, CreateBatchRequest req, UUID by) {
        List<UUID> ids = req.claimIds() == null ? List.of() : req.claimIds().stream().distinct().toList();
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtext('expbatch:" + tenantId.getValue() + "'))");
        List<BatchClaim> claims = ids.isEmpty() ? List.of() : jdbc.query("""
                SELECT id, claim_number, status, amount FROM expense_claims WHERE tenant_id = ? AND id = ANY (?)""",
                (rs, n) -> new BatchClaim(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)),
                tenantId.getValue(), ids.toArray(new UUID[0]));
        Set<UUID> batched = new HashSet<>(ids.isEmpty() ? List.of() : jdbc.query("""
                SELECT i.claim_id FROM expense_reimbursement_batch_items i JOIN expense_reimbursement_batches b ON b.id = i.batch_id
                 WHERE b.tenant_id = ? AND b.status = 'DRAFT' AND i.claim_id = ANY (?)""",
                (rs, n) -> rs.getObject(1, UUID.class), tenantId.getValue(), ids.toArray(new UUID[0])));
        String problem = ExpensePolicyRules.batchProblem(claims, batched, ids.size());
        if (problem != null) throw new IllegalArgumentException(problem);

        Integer count = jdbc.queryForObject("SELECT count(*) FROM expense_reimbursement_batches WHERE tenant_id = ?", Integer.class, tenantId.getValue());
        String number = String.format("REB-%d-%04d", LocalDate.now().getYear(), (count == null ? 0 : count) + 1);
        UUID batchId = UUID.randomUUID();
        jdbc.update("INSERT INTO expense_reimbursement_batches (id, tenant_id, batch_number, total_amount, created_by) VALUES (?,?,?,?,?)",
                batchId, tenantId.getValue(), number, ExpensePolicyRules.total(claims), by);
        for (BatchClaim c : claims) {
            jdbc.update("""
                    INSERT INTO expense_reimbursement_batch_items (batch_id, claim_id, tenant_id, employee_name, claim_number, amount)
                    SELECT ?, id, tenant_id, employee_name, claim_number, amount FROM expense_claims WHERE id = ? AND tenant_id = ?""",
                    batchId, c.id(), tenantId.getValue());
        }
        return get(tenantId, batchId);
    }

    /** Pays every claim in the batch. All or nothing: a claim that cannot be paid (for example one that pays the payer) stops the batch. */
    @Transactional
    public BatchResponse pay(TenantId tenantId, UUID batchId, PayBatchRequest req, UUID by) {
        BatchResponse b = get(tenantId, batchId);
        boolean needsBank = !accountingFacade.getBankAccounts(tenantId).isEmpty();
        String problem = ExpensePolicyRules.payProblem(b.status(), needsBank, req.bankAccountId());
        if (problem != null) throw new IllegalArgumentException(problem);
        String ref = req.reference() == null || req.reference().isBlank() ? b.batchNumber() : req.reference().trim();
        for (BatchItem item : b.items()) {
            try {
                expenses.markReimbursed(tenantId, item.claimId(), by, req.bankAccountId(), ref);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Claim " + item.claimNumber() + " could not be paid, so the batch was not paid: " + e.getMessage(), e);
            }
        }
        jdbc.update("""
                UPDATE expense_reimbursement_batches SET status = 'PAID', bank_account_id = ?, reference = ?, paid_by = ?, paid_at = now()
                 WHERE id = ? AND tenant_id = ?""", req.bankAccountId(), ref, by, batchId, tenantId.getValue());
        return get(tenantId, batchId);
    }

    @Transactional
    public BatchResponse cancel(TenantId tenantId, UUID batchId) {
        BatchResponse b = get(tenantId, batchId);
        if (!"DRAFT".equals(b.status())) throw new IllegalStateException("Only an unpaid batch can be cancelled.");
        jdbc.update("UPDATE expense_reimbursement_batches SET status = 'CANCELLED' WHERE id = ? AND tenant_id = ?", batchId, tenantId.getValue());
        return get(tenantId, batchId);
    }
}
