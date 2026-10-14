package za.co.handyflow.platform.expenses.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.expenses.application.internal.ExpensePolicyRules.Finding;
import za.co.handyflow.platform.expenses.application.internal.ExpensePolicyRules.Policy;
import za.co.handyflow.platform.expenses.domain.model.ExpenseClaim;
import za.co.handyflow.platform.expenses.dto.ExpensePolicyDtos.PolicyRequest;
import za.co.handyflow.platform.expenses.dto.ExpensePolicyDtos.PolicyResponse;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Expense policies and the checks run when a claim is submitted (finance F18). */
@Service
@RequiredArgsConstructor
public class ExpensePolicyService {

    public static final List<String> CATEGORIES = List.of("TRAVEL", "MEALS", "ACCOMMODATION", "FUEL", "EQUIPMENT",
            "OFFICE_SUPPLIES", "MARKETING", "ENTERTAINMENT", "TELEPHONE", "OTHER");

    private final JdbcTemplate jdbc;
    private final EvidenceFacade evidenceFacade;

    @Transactional(readOnly = true)
    public List<PolicyResponse> list(TenantId tenantId) {
        return jdbc.query("""
                SELECT id, category, max_per_claim, limit_action, receipt_above, max_age_days FROM expense_policies
                 WHERE tenant_id = ? ORDER BY (category = '*') DESC, category""",
                (rs, n) -> new PolicyResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3), rs.getString(4),
                        rs.getBigDecimal(5), (Integer) rs.getObject(6)), tenantId.getValue());
    }

    /** Saves the policy for a category, replacing any earlier one. */
    @Transactional
    public List<PolicyResponse> save(TenantId tenantId, PolicyRequest req, UUID by) {
        String problem = ExpensePolicyRules.policyProblem(req.category(), req.maxPerClaim(), req.limitAction(), req.receiptAbove(),
                req.maxAgeDays(), CATEGORIES);
        if (problem != null) throw new IllegalArgumentException(problem);
        jdbc.update("""
                INSERT INTO expense_policies (id, tenant_id, category, max_per_claim, limit_action, receipt_above, max_age_days, updated_by)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (tenant_id, category) DO UPDATE SET max_per_claim = EXCLUDED.max_per_claim, limit_action = EXCLUDED.limit_action,
                    receipt_above = EXCLUDED.receipt_above, max_age_days = EXCLUDED.max_age_days, updated_by = EXCLUDED.updated_by, updated_at = now()""",
                UUID.randomUUID(), tenantId.getValue(), req.category(), req.maxPerClaim(),
                req.maxPerClaim() == null ? null : req.limitAction(), req.receiptAbove(), req.maxAgeDays(), by);
        return list(tenantId);
    }

    @Transactional
    public List<PolicyResponse> delete(TenantId tenantId, UUID id) {
        jdbc.update("DELETE FROM expense_policies WHERE id = ? AND tenant_id = ?", id, tenantId.getValue());
        return list(tenantId);
    }

    /** What the policy and the duplicate check make of this claim. Blocking findings stop it being submitted. */
    @Transactional(readOnly = true)
    public List<Finding> evaluate(TenantId tenantId, ExpenseClaim claim) {
        List<Policy> policies = jdbc.query("""
                SELECT category, max_per_claim, limit_action, receipt_above, max_age_days FROM expense_policies WHERE tenant_id = ?""",
                (rs, n) -> new Policy(rs.getString(1), rs.getBigDecimal(2), rs.getString(3), rs.getBigDecimal(4), (Integer) rs.getObject(5)),
                tenantId.getValue());
        boolean hasReceipt = (claim.getReceiptUrl() != null && !claim.getReceiptUrl().isBlank())
                || !evidenceFacade.listFor(tenantId, "expenses", "ExpenseClaim", claim.getId()).isEmpty();
        List<Finding> out = new ArrayList<>(ExpensePolicyRules.check(ExpensePolicyRules.pick(policies, claim.getCategory()),
                claim.getAmount(), claim.getClaimDate(), LocalDate.now(), hasReceipt));
        out.addAll(duplicates(tenantId, claim));
        return out;
    }

    private List<Finding> duplicates(TenantId tenantId, ExpenseClaim claim) {
        if (claim.getAmount() == null || claim.getClaimDate() == null) return List.of();
        LocalDate d = claim.getClaimDate();
        int w = ExpensePolicyRules.DUPLICATE_WINDOW_DAYS;
        // The same person: the same employee record when there is one, otherwise the same name.
        record Other(String number, java.math.BigDecimal amount, LocalDate date, String category) {}
        List<Other> others = jdbc.query("""
                SELECT claim_number, amount, claim_date, category FROM expense_claims
                 WHERE tenant_id = ? AND id <> ? AND amount = ? AND claim_date BETWEEN ? AND ?
                   AND status IN ('PENDING','APPROVED','REIMBURSED')
                   AND (CASE WHEN ?::uuid IS NOT NULL THEN employee_id = ?::uuid ELSE lower(employee_name) = lower(?) END)
                 ORDER BY claim_date DESC LIMIT 5""",
                (rs, n) -> new Other(rs.getString(1), rs.getBigDecimal(2), rs.getDate(3).toLocalDate(), rs.getString(4)),
                tenantId.getValue(), claim.getId(), claim.getAmount(), Date.valueOf(d.minusDays(w)), Date.valueOf(d.plusDays(w)),
                claim.getEmployeeId(), claim.getEmployeeId(), claim.getEmployeeName());
        List<Finding> out = new ArrayList<>();
        for (Other o : others) {
            String kind = ExpensePolicyRules.duplicateKind(claim.getAmount(), d, claim.getCategory(), o.amount(), o.date(), o.category());
            if (kind != null) out.add(ExpensePolicyRules.duplicateFinding(kind, o.number()));
        }
        return out;
    }
}
