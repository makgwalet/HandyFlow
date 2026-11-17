package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.FieldEncryptionService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierBankChange;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierBankChangeRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;
import za.co.handyflow.platform.supplychain.dto.BankChangeResponse;
import za.co.handyflow.platform.supplychain.dto.CreateBankChangeRequest;

import java.time.Duration;
import java.util.*;

/**
 * Bank-detail change control — the classic payment-diversion fraud vector.
 * <p>
 * 1. Anyone with supplier-manage rights PROPOSES a change (reason required). Nothing on the
 *    supplier changes. The proposed account number is stored encrypted.
 * 2. Fraud signals are computed and attached as warnings: the account already belongs to
 *    another supplier; the supplier's contact e-mail changed in the last 14 days.
 * 3. The supplier's contact on file is e-mailed that a change was requested (no account
 *    numbers in the e-mail) — a real supplier who didn't ask for it will shout.
 * 4. A DIFFERENT person verifies out-of-band (call-back on a known number, signed letter,
 *    bank confirmation, in person), records the method and a note, and only then are the new
 *    details applied. app.scm.supplier.bank-change.require-independent-verifier=false lets a
 *    single-user tenant verify its own change — leave it true wherever there are two people.
 * Every step is audited. AP should refuse to pay a supplier while hasPendingChange() is true.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierBankChangeService {

    private static final Set<String> METHODS = Set.of("CALLBACK", "SIGNED_LETTER", "BANK_CONFIRMATION", "IN_PERSON");
    private static final Duration RECENT_CONTACT_CHANGE = Duration.ofDays(14);

    private final ScSupplierRepository supplierRepo;
    private final ScSupplierBankChangeRepository changeRepo;
    private final SupplierAuditService audit;
    private final FieldEncryptionService encryption;
    private final ControlExceptionFacade controlExceptionFacade;
    private final EmailService emailService;

    @Value("${app.scm.supplier.bank-change.require-independent-verifier:true}")
    private boolean requireIndependentVerifier;

    @Transactional
    public BankChangeResponse request(TenantId tenantId, UUID userId, String userName, UUID supplierId,
                                      CreateBankChangeRequest req) {
        UUID tid = tenantId.getValue();
        ScSupplier s = supplier(tenantId, supplierId);
        if (s.getStatus() == SupplierStatus.INACTIVE)
            throw new HandyFlowException("Reopen the supplier before changing its bank details", HttpStatus.CONFLICT, "INVALID_SUPPLIER_STATE");
        if (req.reason() == null || req.reason().isBlank())
            throw new HandyFlowException("A reason for the change is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        if (req.bankName() == null || req.bankName().isBlank())
            throw new IllegalArgumentException("Bank name is required");
        SupplierBankRules.Bank bank = SupplierBankRules.validateRequired(req.accountNumber(), req.branchCode());

        if (bank.account().equals(s.getBankAccount()))
            throw new HandyFlowException("These are already the supplier's bank details", HttpStatus.BAD_REQUEST, "NO_CHANGES");
        if (changeRepo.findOpenForSupplier(tid, supplierId).isPresent())
            throw new HandyFlowException("A bank change for this supplier is already awaiting verification",
                    HttpStatus.CONFLICT, "BANK_CHANGE_PENDING");

        List<String> warnings = warnings(tid, s, bank.account());
        ScSupplierBankChange c = changeRepo.save(ScSupplierBankChange.propose(tid, supplierId, req.bankName().trim(),
                encryption.encrypt(bank.account()), SupplierBankRules.mask(bank.account()), bank.branch(),
                s.getBankName(), SupplierBankRules.mask(s.getBankAccount()),
                req.reason().trim(), String.join(" | ", warnings), userId, userName));

        audit.record(tid, supplierId, "BANK_CHANGE_REQUESTED", null, null,
                "Proposed " + c.getProposedBankName() + " " + c.getProposedAccountMasked()
                        + " (was " + Optional.ofNullable(c.getPreviousAccountMasked()).orElse("none") + "): " + c.getReason()
                        + (warnings.isEmpty() ? "" : " [WARNINGS: " + String.join("; ", warnings) + "]"),
                userId, userName);
        controlExceptionFacade.raise(tenantId, "supplychain", "SUPPLIER_BANK_CHANGE_PENDING", "ScSupplierBankChange",
                c.getId(), warnings.isEmpty() ? "WARNING" : "CRITICAL",
                "Bank change for " + s.getName() + " awaiting independent verification"
                        + (warnings.isEmpty() ? "" : " — " + String.join("; ", warnings)));
        notifySupplier(s, "A request to change your bank details was received",
                "<p>We received a request to change the bank details we hold for <strong>" + HtmlUtils.htmlEscape(s.getName())
                        + "</strong>.</p><p>The change will <strong>not</strong> take effect until we have verified it with you. "
                        + "If you did not ask for this, please contact us immediately.</p>");
        log.info("[SCM] Bank change requested for supplier {} by {} (warnings={})", s.getName(), userName, warnings.size());
        return toResponse(c);
    }

    @Transactional
    public BankChangeResponse verify(TenantId tenantId, UUID changeId, UUID userId, String userName,
                                     String method, String note) {
        UUID tid = tenantId.getValue();
        ScSupplierBankChange c = changeRepo.lockByTenantAndId(tid, changeId)
                .orElseThrow(() -> new HandyFlowException("Bank change not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!c.isPending())
            throw new HandyFlowException("Bank change is already " + c.getStatus(), HttpStatus.CONFLICT, "BANK_CHANGE_CLOSED");
        if (requireIndependentVerifier && c.getRequestedBy() != null && c.getRequestedBy().equals(userId))
            throw new HandyFlowException("Independent verification required: you cannot verify a bank change you requested",
                    HttpStatus.FORBIDDEN, "INDEPENDENT_VERIFIER_REQUIRED");
        String m = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(m))
            throw new HandyFlowException("Verification method must be one of " + METHODS, HttpStatus.BAD_REQUEST, "INVALID_METHOD");
        if (note == null || note.isBlank())
            throw new HandyFlowException("Record how the change was verified (who you spoke to, on which number, etc.)",
                    HttpStatus.BAD_REQUEST, "NOTE_REQUIRED");

        ScSupplier s = supplier(tenantId, c.getSupplierId());
        String account = encryption.decrypt(c.getProposedAccountEnc());
        String previous = SupplierBankRules.mask(s.getBankAccount());
        s.applyBankDetails(c.getProposedBankName(), account, c.getProposedBranchCode());
        supplierRepo.save(s);

        c.markApplied(userId, userName, m, note.trim());
        changeRepo.save(c);

        audit.record(tid, s.getId(), "BANK_CHANGE_APPLIED", null, null,
                "Bank details changed " + Optional.ofNullable(previous).orElse("none") + " -> " + c.getProposedAccountMasked()
                        + ", verified by " + m + ": " + note.trim(), userId, userName);
        controlExceptionFacade.resolveForEntity(tenantId, "ScSupplierBankChange", c.getId(), userId, userName,
                "Verified by " + m);
        notifySupplier(s, "Your bank details were updated",
                "<p>The bank details we hold for <strong>" + HtmlUtils.htmlEscape(s.getName())
                        + "</strong> were updated (account ending " + HtmlUtils.htmlEscape(c.getProposedAccountMasked().replace("*", ""))
                        + ") after verification.</p><p>If this was not you, contact us immediately.</p>");
        log.info("[SCM] Bank change {} applied to supplier {} (verified by {} via {})", changeId, s.getName(), userName, m);
        return toResponse(c);
    }

    @Transactional
    public BankChangeResponse reject(TenantId tenantId, UUID changeId, UUID userId, String userName, String note) {
        ScSupplierBankChange c = pending(tenantId, changeId);
        if (note == null || note.isBlank())
            throw new HandyFlowException("A rejection reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        c.markRejected(userId, userName, note.trim());
        changeRepo.save(c);
        audit.record(tenantId.getValue(), c.getSupplierId(), "BANK_CHANGE_REJECTED", null, null, note.trim(), userId, userName);
        controlExceptionFacade.resolveForEntity(tenantId, "ScSupplierBankChange", c.getId(), userId, userName, "Rejected: " + note);
        return toResponse(c);
    }

    @Transactional
    public BankChangeResponse cancel(TenantId tenantId, UUID changeId, UUID userId, String userName) {
        ScSupplierBankChange c = pending(tenantId, changeId);
        if (c.getRequestedBy() != null && !c.getRequestedBy().equals(userId))
            throw new HandyFlowException("Only the requester can cancel a bank change (others can reject it)",
                    HttpStatus.FORBIDDEN, "NOT_REQUESTER");
        c.markCancelled(userId, userName, "Cancelled by requester");
        changeRepo.save(c);
        audit.record(tenantId.getValue(), c.getSupplierId(), "BANK_CHANGE_CANCELLED", null, null, null, userId, userName);
        controlExceptionFacade.resolveForEntity(tenantId, "ScSupplierBankChange", c.getId(), userId, userName, "Cancelled by requester");
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<BankChangeResponse> list(TenantId tenantId, UUID supplierId) {
        supplier(tenantId, supplierId);
        return changeRepo.findAllForSupplier(tenantId.getValue(), supplierId).stream().map(this::toResponse).toList();
    }

    /** For AP / payment runs: true while a bank change is unverified — hold payments to this supplier. */
    @Transactional(readOnly = true)
    public boolean hasPendingChange(TenantId tenantId, UUID supplierId) {
        return changeRepo.findOpenForSupplier(tenantId.getValue(), supplierId).isPresent();
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private List<String> warnings(UUID tid, ScSupplier s, String newAccount) {
        List<String> w = new ArrayList<>();
        if (supplierRepo.countOtherWithBankAccount(tid, newAccount, s.getId()) > 0)
            w.add("This account number is already on another supplier record");
        if (audit.happenedWithin(tid, s.getId(), "CONTACT_EMAIL_CHANGED", RECENT_CONTACT_CHANGE))
            w.add("The supplier's contact e-mail was changed in the last 14 days");
        return w;
    }

    private void notifySupplier(ScSupplier s, String subject, String html) {
        try {
            if (s.getContactEmail() != null && !s.getContactEmail().isBlank())
                emailService.send(s.getContactEmail(), subject, html);
        } catch (Exception e) {
            log.warn("[SCM] Could not notify supplier {} about bank change: {}", s.getName(), e.getMessage());
        }
    }

    private ScSupplierBankChange pending(TenantId tenantId, UUID changeId) {
        ScSupplierBankChange c = changeRepo.lockByTenantAndId(tenantId.getValue(), changeId)
                .orElseThrow(() -> new HandyFlowException("Bank change not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!c.isPending())
            throw new HandyFlowException("Bank change is already " + c.getStatus(), HttpStatus.CONFLICT, "BANK_CHANGE_CLOSED");
        return c;
    }

    private ScSupplier supplier(TenantId tenantId, UUID id) {
        return supplierRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private BankChangeResponse toResponse(ScSupplierBankChange c) {
        return new BankChangeResponse(c.getId(), c.getSupplierId(), c.getStatus(), c.getProposedBankName(),
                c.getProposedAccountMasked(), c.getProposedBranchCode(), c.getPreviousBankName(),
                c.getPreviousAccountMasked(), c.getReason(), c.getWarnings(), c.getRequestedByName(),
                c.getCreatedAt(), c.getVerifiedByName(), c.getVerifiedAt(), c.getVerificationMethod(),
                c.getVerificationNote());
    }
}
