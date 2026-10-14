package za.co.handyflow.platform.expenses.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.expenses.application.internal.ExpenseLifecycle.Action;
import za.co.handyflow.platform.expenses.domain.model.ExpenseClaim;
import za.co.handyflow.platform.shared.HandyFlowException;
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
    private final za.co.handyflow.platform.evidence.application.EvidenceFacade evidenceFacade;
    // Finance patch F7: claims go through the approval engine (platform default: one EXPENSES_MANAGE approver).
    private final ApprovalFacade approvalFacade;
    private final za.co.handyflow.platform.accounting.application.AccountingFacade accountingFacade;
    // Finance patch F18: limits, receipt rules and duplicate checks at submission.
    private final ExpensePolicyService policyService;

    private static final String MODULE = "expenses", ENTITY = "CLAIM";

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

    /** F1: the list as this person may see it. Without EXPENSES_READ, only the claims they submitted. */
    @Transactional(readOnly = true)
    public Page<ExpenseClaimResponse> getClaimsFor(TenantId tenantId, String status, UUID employeeId, Pageable pageable,
                                                   boolean seesAll, UUID viewer) {
        if (seesAll) return getClaims(tenantId, status, employeeId, pageable);
        if (viewer == null) return Page.empty(pageable);
        return claimRepo.findMine(tenantId, viewer, status, pageable).map(this::toResponse);
    }

    /** F1: one claim, if this person may see it; otherwise "not found", so its existence is not revealed. */
    @Transactional(readOnly = true)
    public ExpenseClaimResponse getClaimFor(TenantId tenantId, UUID id, boolean seesAll, UUID viewer) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        if (!ExpenseRules.maySee(seesAll, claim.getSubmittedBy(), viewer)) throw new ResourceNotFoundException("ExpenseClaim", id.toString());
        return toResponse(claim);
    }

    /** F1: the check before receipts are listed or attached. */
    @Transactional(readOnly = true)
    public void requireAccess(TenantId tenantId, UUID id, boolean allowed, UUID user) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        if (!allowed && (user == null || !user.equals(claim.getSubmittedBy()))) throw new ResourceNotFoundException("ExpenseClaim", id.toString());
    }

    /** The approver's email, to stop someone approving a claim that pays them. */
    private String userEmail(UUID userId) {
        if (userId == null) return null;
        try { return jdbc.queryForList("SELECT email FROM users WHERE id = ?", String.class, userId).stream().findFirst().orElse(null); }
        catch (RuntimeException e) { return null; }
    }

    private String employeeEmail(TenantId tenantId, UUID employeeId) {
        if (employeeId == null) return null;
        try { return hrFacade.findEmployeeById(tenantId, employeeId).map(EmployeeResponse::email).orElse(null); }
        catch (RuntimeException e) { return null; }
    }

    /**
     * Saves a new claim (finance patch F7): as a draft when {@code asDraft}, otherwise sent for approval at once (what the
     * form did before drafts existed).
     */
    @Transactional
    public ExpenseClaimResponse submitClaim(TenantId tenantId, UUID submittedBy,
                                            CreateExpenseClaimRequest req, boolean asDraft) {
        String number = numberGen.next(tenantId);
        ExpenseClaim claim = ExpenseClaim.create(tenantId, number,
                req.employeeId(), submittedBy, req.employeeName(),
                req.claimDate(), req.category(), req.description(),
                req.amount(), req.receiptUrl(), req.notes());
        claimRepo.save(claim);
        if (asDraft) {
            log.info("Expense claim {} saved as draft by={}", number, submittedBy);
            return toResponse(claim);
        }
        return sendForApproval(tenantId, claim);
    }

    /** Changes a draft or a rejected claim; only the claimant (finance patch F7). */
    @Transactional
    public ExpenseClaimResponse updateClaim(TenantId tenantId, UUID id, UUID user, CreateExpenseClaimRequest req) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        requireClaimant(claim, user);
        claim.edit(req.employeeId(), req.employeeName(), req.claimDate(), req.category(), req.description(),
                req.amount(), req.receiptUrl(), req.notes());
        claimRepo.save(claim);
        return toResponse(claim);
    }

    /** Sends a draft, or a fixed rejected claim, for approval; only the claimant (finance patch F7). */
    @Transactional
    public ExpenseClaimResponse submitExisting(TenantId tenantId, UUID id, UUID user) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        requireClaimant(claim, user);
        return sendForApproval(tenantId, claim);
    }

    /** The claimant withdraws a draft or a claim waiting for approval (finance patch F7). */
    @Transactional
    public ExpenseClaimResponse withdrawClaim(TenantId tenantId, UUID id, UUID user) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        requireClaimant(claim, user);
        claim.withdraw();
        claimRepo.save(claim);
        log.info("Expense claim {} withdrawn by={}", claim.getClaimNumber(), user);
        return toResponse(claim);
    }

    private ExpenseClaimResponse sendForApproval(TenantId tenantId, ExpenseClaim claim) {
        String incomplete = ExpenseLifecycle.submitProblem(claim.getEmployeeName(), claim.getClaimDate(),
                claim.getCategory(), claim.getDescription(), claim.getAmount(), LocalDate.now());
        if (incomplete != null) throw new HandyFlowException(incomplete, HttpStatus.BAD_REQUEST, "CLAIM_INCOMPLETE");
        // Finance patch F18: a claim that breaks a blocking rule is not sent; warnings travel with it to the approver.
        var findings = policyService.evaluate(tenantId, claim);
        if (ExpensePolicyRules.blocked(findings)) {
            throw new HandyFlowException(ExpensePolicyRules.blockText(findings), HttpStatus.BAD_REQUEST, "CLAIM_BREAKS_POLICY");
        }
        claim.recordPolicyFlags(String.join("\n", findings.stream().map(ExpensePolicyRules.Finding::text).toList()));
        boolean resubmission = "REJECTED".equals(claim.getStatus());
        claim.submit();
        claimRepo.save(claim);

        var latest = approvalFacade.getLatestRequestForEntity(tenantId, MODULE, ENTITY, claim.getId());
        ApprovalRequestResponse request = resubmission && latest.isPresent() && "REJECTED".equals(latest.get().status())
                ? approvalFacade.resubmit(tenantId, latest.get().id(), claim.getSubmittedBy(), approvalMetadata(tenantId, claim))
                : approvalFacade.submit(tenantId, MODULE, ENTITY, claim.getId(), claim.getSubmittedBy(), approvalMetadata(tenantId, claim));
        log.info("Expense claim {} sent for approval (submission {}) by={} amount={}",
                claim.getClaimNumber(), claim.getSubmissionCount(), claim.getSubmittedBy(), claim.getAmount());
        if ("APPROVED".equals(request.status())) {
            // A tenant rule with no step that applies to this claim approves it at once.
            completeApproval(tenantId, claim.getId(), null);
            return toResponse(findByTenant(tenantId, claim.getId()));
        }
        notifySubmitted(tenantId, claim);
        return toResponse(claim);
    }

    /**
     * Gives this person's approval (finance patch F7). The claim is approved, and posted to the ledger, when the approval
     * engine's last step is done; with the default rule that is the first approval. Claims sent before F7 have no
     * approval request yet; one is made here.
     */
    @Transactional
    public ExpenseClaimResponse approveClaim(TenantId tenantId, UUID id,
                                             UUID approvedBy) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        requireAction(claim, Action.APPROVE);
        String problem = ExpenseRules.selfApprovalProblem(claim.getSubmittedBy(), approvedBy,
                employeeEmail(tenantId, claim.getEmployeeId()), userEmail(approvedBy));
        if (problem != null) throw new IllegalStateException(problem);

        ApprovalRequestResponse result = actOnPendingStep(tenantId, claim, approvedBy, "APPROVE", null);
        if ("APPROVED".equals(result.status())) {
            completeApproval(tenantId, id, approvedBy);
        } else {
            log.info("Expense claim {} approval recorded by={}, further approval still needed", claim.getClaimNumber(), approvedBy);
        }
        return toResponse(findByTenant(tenantId, id));
    }

    @Transactional
    public ExpenseClaimResponse rejectClaim(TenantId tenantId, UUID id,
                                            UUID approvedBy, String reason) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        requireAction(claim, Action.REJECT);
        String noReason = ExpenseLifecycle.reasonProblem(reason);
        if (noReason != null) throw new HandyFlowException(noReason, HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        actOnPendingStep(tenantId, claim, approvedBy, "REJECT", reason.trim());
        rejectFromEngine(tenantId, id, approvedBy, reason.trim());
        return toResponse(findByTenant(tenantId, id));
    }

    /**
     * Approves the claim and posts it, once (finance patch F7). Called when this module's approve completes the last step,
     * and by ExpensesApprovalEventHandler when the last step is done from the Approvals inbox; whichever runs second finds
     * the claim no longer waiting and does nothing.
     */
    @Transactional
    public void completeApproval(TenantId tenantId, UUID id, UUID approvedBy) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        if (!"PENDING".equals(claim.getStatus())) return;
        claim.approve(approvedBy != null ? approvedBy : lastApprover(tenantId, id));
        claimRepo.save(claim);
        postToAccounting(tenantId, claim);
        log.info("Expense claim {} approved amount={}", claim.getClaimNumber(), claim.getAmount());
        notifyEmployee(tenantId, claim, NotificationType.EXPENSE_CLAIM_APPROVED,
                "Expense claim approved: " + claim.getClaimNumber(),
                "Your expense claim " + claim.getClaimNumber() + " for R"
                        + claim.getAmount().stripTrailingZeros().toPlainString()
                        + " has been approved and will be paid out.");
    }

    /** Rejects the claim, once; the claimant can fix and resubmit it (finance patch F7). */
    @Transactional
    public void rejectFromEngine(TenantId tenantId, UUID id, UUID by, String reason) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        if (!"PENDING".equals(claim.getStatus())) return;
        String why = reason != null ? reason : rejectionComment(tenantId, id);
        claim.reject(by, why);
        claimRepo.save(claim);
        log.info("Expense claim {} rejected reason={}", claim.getClaimNumber(), why);
        notifyEmployee(tenantId, claim, NotificationType.EXPENSE_CLAIM_REJECTED,
                "Expense claim rejected: " + claim.getClaimNumber(),
                "Your expense claim " + claim.getClaimNumber() + " was rejected. Reason: "
                        + (why != null && !why.isBlank() ? why : "No reason provided")
                        + ". You can correct it and submit it again.");
    }

    /**
     * Pays the claim out (finance patch F7) and posts the payment: Dr Accrued Expenses, Cr the bank account it was paid
     * from. Where the business keeps bank accounts in Accounting, the account is required. Never by the claimant or the
     * person it pays.
     */
    @Transactional
    public ExpenseClaimResponse markReimbursed(TenantId tenantId, UUID id, UUID by, UUID bankAccountId, String reference) {
        ExpenseClaim claim = findByTenant(tenantId, id);
        requireAction(claim, Action.REIMBURSE);
        String self = ExpenseRules.selfApprovalProblem(claim.getSubmittedBy(), by,
                employeeEmail(tenantId, claim.getEmployeeId()), userEmail(by));
        if (self != null) throw new IllegalStateException(self.replace("approve", "pay out").replace("approved", "paid out"));
        boolean keepsBankAccounts = !accountingFacade.getBankAccounts(tenantId).isEmpty();
        if (bankAccountId == null && keepsBankAccounts)
            throw new HandyFlowException("Choose the bank account the claim was paid from", HttpStatus.BAD_REQUEST, "BANK_ACCOUNT_REQUIRED");
        String ref = reference == null || reference.isBlank() ? null : reference.trim();
        UUID journalId = bankAccountId == null ? null : accountingPoster.postReimbursementJournal(tenantId, claim.getId(),
                claim.getClaimNumber(), claim.getEmployeeName(), claim.getAmount(), bankAccountId, ref).orElse(null);
        claim.markReimbursed(by, bankAccountId, ref, journalId);
        claimRepo.save(claim);
        log.info("Expense claim {} reimbursed by={} journal={}", claim.getClaimNumber(), by, journalId);
        notifyEmployee(tenantId, claim, NotificationType.EXPENSE_CLAIM_REIMBURSED,
                "Expense claim reimbursed: " + claim.getClaimNumber(),
                "Your expense claim " + claim.getClaimNumber() + " for R"
                        + claim.getAmount().stripTrailingZeros().toPlainString()
                        + " has been paid out.");
        return toResponse(claim);
    }

    // ── Approval engine helpers (finance patch F7) ────────────────────────────

    private ApprovalRequestResponse actOnPendingStep(TenantId tenantId, ExpenseClaim claim, UUID actor,
                                                     String decision, String comment) {
        var latest = approvalFacade.getLatestRequestForEntity(tenantId, MODULE, ENTITY, claim.getId());
        ApprovalRequestResponse request = latest.filter(this::isOpen).orElse(null);
        if (request == null) {
            // Sent before F7 (or the engine request is missing): open one now, then act on it.
            request = approvalFacade.submit(tenantId, MODULE, ENTITY, claim.getId(), claim.getSubmittedBy(),
                    approvalMetadata(tenantId, claim));
            if (!isOpen(request)) return request;
        }
        ApprovalStepResponse step = request.steps().stream()
                .filter(st -> "PENDING".equals(st.status()))
                .min(java.util.Comparator.comparingInt(ApprovalStepResponse::stepOrder))
                .orElseThrow(() -> new HandyFlowException("This claim's approval has no step waiting — needs a manual look",
                        HttpStatus.CONFLICT, "NO_PENDING_STEP"));
        return approvalFacade.actOnStep(tenantId, step.id(), actor, currentAuthorities(), decision, comment, null);
    }

    /** The approval engine's view of a claim; the claimant and the person paid may not approve any step. */
    private java.util.Map<String, Object> approvalMetadata(TenantId tenantId, ExpenseClaim claim) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("totalAmount", claim.getAmount());
        m.put("category", claim.getCategory());
        java.util.Set<String> excluded = new java.util.LinkedHashSet<>();
        if (claim.getSubmittedBy() != null) excluded.add(claim.getSubmittedBy().toString());
        String payee = employeeEmail(tenantId, claim.getEmployeeId());
        if (payee != null && !payee.isBlank()) {
            try {
                excluded.addAll(jdbc.queryForList("SELECT id::text FROM users WHERE tenant_id = ? AND lower(email) = lower(?)",
                        String.class, tenantId.getValue(), payee.trim()));
            } catch (RuntimeException ignored) { /* the claimant check still applies */ }
        }
        if (!excluded.isEmpty()) m.put("excludedActors", List.copyOf(excluded));
        return m;
    }

    private boolean isOpen(ApprovalRequestResponse r) {
        return "SUBMITTED".equals(r.status()) || "IN_PROGRESS".equals(r.status());
    }

    private UUID lastApprover(TenantId tenantId, UUID id) {
        return approvalFacade.getLatestRequestForEntity(tenantId, MODULE, ENTITY, id)
                .flatMap(r -> r.steps().stream().filter(st -> st.actedBy() != null)
                        .max(java.util.Comparator.comparingInt(ApprovalStepResponse::stepOrder)))
                .map(ApprovalStepResponse::actedBy).orElse(null);
    }

    private String rejectionComment(TenantId tenantId, UUID id) {
        return approvalFacade.getLatestRequestForEntity(tenantId, MODULE, ENTITY, id)
                .flatMap(r -> r.steps().stream().filter(st -> "REJECTED".equals(st.status())).findFirst())
                .map(ApprovalStepResponse::comment).orElse(null);
    }

    private List<String> currentAuthorities() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private void requireClaimant(ExpenseClaim claim, UUID user) {
        String p = ExpenseLifecycle.claimantProblem(claim.getSubmittedBy(), user);
        if (p != null) throw new HandyFlowException(p, HttpStatus.FORBIDDEN, "NOT_CLAIMANT");
    }

    private void requireAction(ExpenseClaim claim, Action action) {
        String p = ExpenseLifecycle.problem(action, claim.getStatus());
        if (p != null) throw new IllegalStateException(p);
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthlyTotal(TenantId tenantId, int month, int year) {
        return claimRepo.sumApprovedByMonth(tenantId, month, year);
    }

    // NEW: real receipt attachment — Stage 0 of the Financial Control &
    // Assurance adoption plan. Deliberately a SEPARATE endpoint from
    // submitClaim() rather than folding file upload into claim
    // creation — submitClaim() stays JSON/@RequestBody exactly as it
    // already works, matching the same "attach to an existing entity"
    // shape already proven by Payroll Bureau's employee documents and
    // Recruitment Agency's CV upload. receiptUrl (the old pasted-link
    // field) is untouched — old claims keep working exactly as before;
    // this is additive, not a migration of existing data.
    @Transactional
    public za.co.handyflow.platform.evidence.dto.EvidenceResponse attachReceipt(
            TenantId tenantId, UUID claimId,
            org.springframework.web.multipart.MultipartFile file,
            UUID uploadedBy, String uploadedByName) {
        ExpenseClaim claim = findByTenant(tenantId, claimId);
        return evidenceFacade.attach(tenantId, file, "RECEIPT", "expenses",
                "ExpenseClaim", claim.getId(), null, uploadedBy, uploadedByName);
    }

    @Transactional(readOnly = true)
    public List<za.co.handyflow.platform.evidence.dto.EvidenceResponse> getReceipts(TenantId tenantId, UUID claimId) {
        findByTenant(tenantId, claimId); // confirms the claim exists and belongs to this tenant before listing
        return evidenceFacade.listFor(tenantId, "expenses", "ExpenseClaim", claimId);
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
                c.getReimbursedAt(), c.getCreatedAt(),
                c.getSubmittedBy(), c.getSubmittedAt(), c.getSubmissionCount(), c.getCancelledAt(),
                c.getReimbursedBy(), c.getReimbursementReference(), c.getReimbursementJournalId(),
                c.getPolicyFlags() == null ? List.of() : List.of(c.getPolicyFlags().split("\n")));
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