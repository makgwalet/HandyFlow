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
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpensesService {

    private final ExpenseClaimRepository claimRepo;
    private final ClaimNumberGenerator   numberGen;
    private final za.co.handyflow.platform.hr.application.HrFacade hrFacade;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ExpenseAccountingPoster accountingPoster;

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
        notifySubmitted(tenantId, claim);
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
        notifyEmployee(tenantId, claim, NotificationType.EXPENSE_CLAIM_APPROVED,
                "Expense claim approved: " + claim.getClaimNumber(),
                "Your expense claim " + claim.getClaimNumber() + " for R"
                        + claim.getAmount().stripTrailingZeros().toPlainString()
                        + " has been approved and will be paid out.");
        return toResponse(claim);
    }

    @Transactional
    public ExpenseClaimResponse rejectClaim(TenantId tenantId, UUID id,
                                            UUID approvedBy, String reason) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        claim.reject(approvedBy, reason);
        claimRepo.save(claim);
        log.info("Expense claim {} rejected reason={}", claim.getClaimNumber(), reason);
        notifyEmployee(tenantId, claim, NotificationType.EXPENSE_CLAIM_REJECTED,
                "Expense claim rejected: " + claim.getClaimNumber(),
                "Your expense claim " + claim.getClaimNumber() + " was rejected. Reason: "
                        + (reason != null && !reason.isBlank() ? reason : "No reason provided") + ".");
        return toResponse(claim);
    }

    @Transactional
    public ExpenseClaimResponse markReimbursed(TenantId tenantId, UUID id) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        claim.markReimbursed();
        claimRepo.save(claim);
        log.info("Expense claim {} reimbursed", claim.getClaimNumber());
        notifyEmployee(tenantId, claim, NotificationType.EXPENSE_CLAIM_REIMBURSED,
                "Expense claim reimbursed: " + claim.getClaimNumber(),
                "Your expense claim " + claim.getClaimNumber() + " for R"
                        + claim.getAmount().stripTrailingZeros().toPlainString()
                        + " has been paid out.");
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
                accountingPoster.postExpenseClaimJournal(
                                tenantId, claim.getId(), claim.getClaimNumber(), claim.getDescription(),
                                claim.getCategory(), claim.getEmployeeName(), claim.getAmount()
                                ).ifPresentOrElse(
                                jeId -> {
                                        claim.linkJournalEntry(jeId);
                                        claimRepo.save(claim);
                                    },
                                () -> log.warn("Accounting journal not posted for claim {} — approval proceeds without it",
                                                claim.getClaimNumber())
                                );
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

    // NEW (Tier 1 gap analysis): no explicit approver field exists on
    // ExpenseClaim, so submission notifications go to tenant admins —
    // same fallback every other module in this codebase uses (Fuel,
    // SCM, Fleet) when there's no single obvious "owner" of the event.
    private void notifySubmitted(TenantId tenantId, ExpenseClaim claim) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.EXPENSE_CLAIM_SUBMITTED)
                .title("Expense claim submitted: " + claim.getClaimNumber())
                .message(claim.getEmployeeName() + " submitted a claim for R"
                        + claim.getAmount().stripTrailingZeros().toPlainString()
                        + " (" + claim.getCategory() + ") — needs approval.")
                .actionUrl("/expenses/" + claim.getId())
                .sourceModule("expenses")
                .sourceEntityId(claim.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyEmployee(TenantId tenantId, ExpenseClaim claim, NotificationType type,
                                String title, String message) {
        if (claim.getEmployeeId() == null) return;
               Optional<EmployeeResponse> employee = hrFacade.findEmployeeById(tenantId, claim.getEmployeeId());
                if (employee.isEmpty() || employee.get().email() == null || employee.get().email().isBlank()) return;
                Recipient recipient = Recipient.external(
                                employee.get().fullName(), employee.get().email(), employee.get().phone());
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl("/expenses/" + claim.getId())
                .sourceModule("expenses")
                .sourceEntityId(claim.getId().toString())
                .recipient(recipient)
                .build());
    }
}