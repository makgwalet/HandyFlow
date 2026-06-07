package za.co.handyflow.platform.expenses.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.expenses.domain.model.ExpenseClaim;
import za.co.handyflow.platform.expenses.domain.repository.ExpenseClaimRepository;
import za.co.handyflow.platform.expenses.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpensesService {

    private final ExpenseClaimRepository claimRepo;
    private final ClaimNumberGenerator   numberGen;
    private final JdbcTemplate           jdbc;

    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> getClaims(TenantId tenantId, String status,
                                                UUID employeeId, Pageable pageable) {
        return claimRepo.findAll(tenantId, status, employeeId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ExpenseClaimResponse getClaim(TenantId tenantId, UUID id) {
        return claimRepo.findByTenantAndId(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseClaim", id.toString()));
    }

    @Transactional
    public ExpenseClaimResponse submitClaim(TenantId tenantId, UUID submittedBy,
                                            CreateExpenseClaimRequest req) {
        String number = numberGen.next(tenantId);
        ExpenseClaim claim = ExpenseClaim.create(tenantId, number,
                req.employeeId(), submittedBy, req.employeeName(),
                req.claimDate(), req.category(), req.description(),
                req.amount(), req.receiptUrl(), req.notes());
        claimRepo.save(claim);
        log.info("Expense claim {} submitted by={} amount={}",
                number, req.employeeName(), req.amount());
        return toResponse(claim);
    }

    @Transactional
    public ExpenseClaimResponse approveClaim(TenantId tenantId, UUID id,
                                             UUID approvedBy) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        claim.approve(approvedBy);
        claimRepo.save(claim);

        // Auto-post to accounting journal
        postToAccounting(tenantId, claim);

        log.info("Expense claim {} approved amount={}", claim.getClaimNumber(),
                claim.getAmount());
        return toResponse(claim);
    }

    @Transactional
    public ExpenseClaimResponse rejectClaim(TenantId tenantId, UUID id,
                                            UUID approvedBy, String reason) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        claim.reject(approvedBy, reason);
        claimRepo.save(claim);
        log.info("Expense claim {} rejected reason={}", claim.getClaimNumber(), reason);
        return toResponse(claim);
    }

    @Transactional
    public ExpenseClaimResponse markReimbursed(TenantId tenantId, UUID id) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        claim.markReimbursed();
        claimRepo.save(claim);
        log.info("Expense claim {} reimbursed", claim.getClaimNumber());
        return toResponse(claim);
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthlyTotal(TenantId tenantId, int month, int year) {
        return claimRepo.sumApprovedByMonth(tenantId, month, year);
    }

    // ── Accounting integration ────────────────────────────────────────────────
    // WHY JDBC here? The accounting module is a separate bounded context.
    // We use JDBC directly rather than calling across module boundaries.
    // This posts: DR Expenses account, CR Accounts Payable account.
    private void postToAccounting(TenantId tenantId, ExpenseClaim claim) {
        try {
            // Find or use default expense account (6000-series)
            var expenseAccountRow = jdbc.queryForMap(
                    "SELECT id FROM acc_accounts WHERE tenant_id = ? AND account_code LIKE '5%' AND account_subtype = 'STAFF_EXPENSES' AND active = true LIMIT 1",
                    tenantId.getValue());
            var payableAccountRow = jdbc.queryForMap(
                    "SELECT id FROM acc_accounts WHERE tenant_id = ? AND account_code LIKE '2%' AND active = true LIMIT 1",
                    tenantId.getValue());

            UUID expenseAccountId = UUID.fromString(expenseAccountRow.get("id").toString());
            UUID payableAccountId = UUID.fromString(payableAccountRow.get("id").toString());

            // Create journal entry
            UUID jeId = UUID.randomUUID();
            String jeNumber = "JE-EXP-" + claim.getClaimNumber();
            LocalDate today = LocalDate.now();

            jdbc.update("""
                INSERT INTO acc_journal_entries
                (id, tenant_id, entry_number, entry_date, description, status, posted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'POSTED', NOW(), NOW(), NOW())
                """,
                    jeId, tenantId.getValue(), jeNumber, today,
                    "Expense claim: " + claim.getDescription() + " (" + claim.getClaimNumber() + ")");

            // DR Expenses
            jdbc.update("""
                INSERT INTO acc_journal_lines
                (id, journal_entry_id, account_id, description, debit_amount, credit_amount, created_at)
                VALUES (?, ?, ?, ?, ?, 0, NOW())
                """,
                    UUID.randomUUID(), jeId, expenseAccountId,
                    claim.getCategory() + " — " + claim.getEmployeeName(),
                    claim.getAmount());

            // CR Accounts Payable
            jdbc.update("""
                INSERT INTO acc_journal_lines
                (id, journal_entry_id, account_id, description, debit_amount, credit_amount, created_at)
                VALUES (?, ?, ?, ?, 0, ?, NOW())
                """,
                    UUID.randomUUID(), jeId, payableAccountId,
                    "Payable to " + claim.getEmployeeName(),
                    claim.getAmount());

            claim.linkJournalEntry(jeId);
            claimRepo.save(claim);
            log.info("Posted expense journal entry {} for claim {}",
                    jeNumber, claim.getClaimNumber());

        } catch (Exception e) {
            // WHY not throw? Approval should not fail just because accounting
            // lookup fails — log the error and let the team fix manually.
            log.error("Failed to post expense {} to accounting: {}",
                    claim.getClaimNumber(), e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ExpenseClaim findByTenant(TenantId tenantId, UUID id) {
        return claimRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseClaim", id.toString()));
    }

    private ExpenseClaimResponse toResponse(ExpenseClaim c) {
        return new ExpenseClaimResponse(c.getId(), c.getClaimNumber(),
                c.getEmployeeId(), c.getEmployeeName(), c.getClaimDate(),
                c.getCategory(), c.getDescription(), c.getAmount(), c.getCurrency(),
                c.getReceiptUrl(), c.getStatus(), c.getRejectionReason(),
                c.getJournalEntryId(), c.getNotes(), c.getApprovedAt(),
                c.getReimbursedAt(), c.getCreatedAt());
    }
}