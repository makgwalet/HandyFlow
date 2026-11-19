package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Maker-checker for contracting. One central place decides whether an action needs a second person's approval, who may give it, and
 * whether an approval still stands.
 *
 * <ul>
 *   <li><b>Policy</b>: per tenant and action. With no row the safe default applies (see {@link ApprovalAction}).</li>
 *   <li><b>Separation of duties</b>: the requester cannot approve their own request, except through a recorded sole-user exception
 *       (only where the policy allows it, only with a reason, always in the audit trail).</li>
 *   <li><b>Invalidation</b>: an approval is bound to a hash of what was reviewed. If the contract, clause or template changes afterwards
 *       the hash no longer matches, the approval cannot be used, and the next look at it marks it invalidated.</li>
 *   <li>An approval is used up by the action it approved.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ContractApprovalService {

    private final ContractApprovalPolicyRepository policyRepo;
    private final ContractApprovalRepository approvalRepo;
    private final ContractApprovalEventRepository eventRepo;
    private final ContractRepository contractRepo;
    private final ContractPartyRepository partyRepo;
    private final ContractClauseRepository clauseRepo;
    private final ContractTemplateRepository templateRepo;
    private final ContractNegotiationProposalRepository proposalRepo;

    public record EffectivePolicy(ApprovalAction action, boolean required, boolean allowSoleUserException, boolean isDefault) {}

    // ── policy ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EffectivePolicy> policies(TenantId tenantId) {
        Map<ApprovalAction, ContractApprovalPolicy> stored = new EnumMap<>(ApprovalAction.class);
        for (ContractApprovalPolicy p : policyRepo.findByTenant(tenantId.getValue())) stored.put(p.getAction(), p);
        List<EffectivePolicy> out = new ArrayList<>();
        for (ApprovalAction a : ApprovalAction.values()) out.add(effective(a, stored.get(a)));
        return out;
    }

    @Transactional(readOnly = true)
    public EffectivePolicy policy(TenantId tenantId, ApprovalAction action) {
        return effective(action, policyRepo.findOne(tenantId.getValue(), action).orElse(null));
    }

    @Transactional
    public EffectivePolicy setPolicy(TenantId tenantId, ApprovalAction action, boolean required, boolean allowSoleUserException, UUID by) {
        ContractApprovalPolicy p = policyRepo.findOne(tenantId.getValue(), action).orElseGet(() -> ContractApprovalPolicy.create(tenantId.getValue(), action));
        p.set(required, allowSoleUserException, by);
        return effective(action, policyRepo.save(p));
    }

    private static EffectivePolicy effective(ApprovalAction a, ContractApprovalPolicy p) {
        // Default: required where the action is risky; a sole-user exception is allowed so a one-person organisation is not locked out,
        // but it still needs a reason and leaves an audit record.
        return p == null ? new EffectivePolicy(a, a.requiredByDefault(), true, true)
                : new EffectivePolicy(a, p.isRequired(), p.isAllowSoleUserException(), false);
    }

    // ── requests ─────────────────────────────────────────────────────────────

    /** Asks for approval of the subject as it is right now. A request already open for it is reused if nothing changed, replaced if it did. */
    @Transactional
    public ContractApproval request(TenantId tenantId, ApprovalAction action, UUID subjectId, UUID by, String note) {
        if (!policy(tenantId, action).required())
            throw new IllegalStateException("Approval is not required for this action");
        Subject s = subject(tenantId, action, subjectId);
        Optional<ContractApproval> open = approvalRepo.findOpenForUpdate(tenantId.getValue(), action, subjectId);
        if (open.isPresent()) {
            ContractApproval existing = open.get();
            if (existing.getSubjectHash().equals(s.hash())) return existing;
            invalidate(existing, "Changed since it was requested");
            approvalRepo.saveAndFlush(existing);
        }
        ContractApproval a = approvalRepo.save(ContractApproval.request(tenantId.getValue(), action, subjectId, s.label(), s.hash(), by, note));
        event(a, "REQUESTED", by, note);
        return a;
    }

    @Transactional
    public ContractApproval approve(TenantId tenantId, UUID id, UUID by, Collection<String> authorities, String note,
                                    boolean soleUserException, String exceptionReason) {
        ContractApproval a = approvalRepo.findForUpdate(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Approval", id.toString()));
        requireApprover(a, authorities);
        if (staleAndInvalidated(tenantId, a)) {
            approvalRepo.save(a);
            throw new IllegalStateException("It has changed since it was requested; ask for approval again");
        }
        a.approve(by, note, soleUserException, exceptionReason, policy(tenantId, a.getAction()).allowSoleUserException());
        event(a, a.isSoleUserException() ? "APPROVED_SOLE_USER_EXCEPTION" : "APPROVED", by,
                a.isSoleUserException() ? "Reason: " + a.getExceptionReason() : note);
        return approvalRepo.save(a);
    }

    @Transactional
    public ContractApproval reject(TenantId tenantId, UUID id, UUID by, Collection<String> authorities, String note) {
        ContractApproval a = approvalRepo.findForUpdate(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Approval", id.toString()));
        requireApprover(a, authorities);
        a.reject(by, note);
        event(a, "REJECTED", by, note);
        return approvalRepo.save(a);
    }

    @Transactional
    public ContractApproval cancel(TenantId tenantId, UUID id, UUID by) {
        ContractApproval a = approvalRepo.findForUpdate(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Approval", id.toString()));
        a.cancel(by);
        event(a, "CANCELLED", by, null);
        return approvalRepo.save(a);
    }

    // ── the check the gated actions call ─────────────────────────────────────

    /**
     * Throws unless the action is allowed to go ahead: either the policy does not require approval, or an approval exists for exactly
     * the subject's current state. Does not change anything when it throws (the caller's transaction rolls back).
     */
    @Transactional
    public void assertApproved(TenantId tenantId, ApprovalAction action, UUID subjectId) {
        if (!policy(tenantId, action).required()) return;
        Subject s = subject(tenantId, action, subjectId);
        Optional<ContractApproval> open = approvalRepo.findOpenForUpdate(tenantId.getValue(), action, subjectId);
        if (open.isEmpty() || !open.get().isApproved())
            throw new IllegalStateException(open.isPresent()
                    ? "Waiting for approval before this can go ahead"
                    : "This needs a second person's approval first. Request approval, then try again.");
        if (!open.get().getSubjectHash().equals(s.hash()))
            throw new IllegalStateException("It has changed since it was approved. Ask for approval again.");
    }

    /** After the gated action has succeeded: uses up the approval so it cannot approve a second time. No-op when approval is not required. */
    @Transactional
    public void consume(TenantId tenantId, ApprovalAction action, UUID subjectId, UUID by) {
        if (!policy(tenantId, action).required()) return;
        approvalRepo.findOpenForUpdate(tenantId.getValue(), action, subjectId).filter(ContractApproval::isApproved).ifPresent(a -> {
            a.consume();
            event(a, "CONSUMED", by, null);
            approvalRepo.save(a);
        });
    }

    // ── reading ──────────────────────────────────────────────────────────────

    /** Requests for the tenant (optionally one status). Open ones whose subject has changed are marked invalidated as they are read. */
    @Transactional
    public List<ContractApproval> list(TenantId tenantId, String status) {
        List<ContractApproval> all = approvalRepo.findByStatus(tenantId.getValue(), status == null || status.isBlank() ? null : status.toUpperCase());
        for (ContractApproval a : all) if (staleAndInvalidated(tenantId, a)) approvalRepo.save(a);
        return status == null || status.isBlank() ? all : all.stream().filter(a -> a.getStatus().equalsIgnoreCase(status)).toList();
    }

    @Transactional
    public ContractApproval get(TenantId tenantId, UUID id) {
        ContractApproval a = approvalRepo.findOne(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Approval", id.toString()));
        if (staleAndInvalidated(tenantId, a)) approvalRepo.save(a);
        return a;
    }

    @Transactional
    public List<ContractApproval> forSubject(TenantId tenantId, ApprovalAction action, UUID subjectId) {
        List<ContractApproval> all = approvalRepo.findBySubject(tenantId.getValue(), action, subjectId);
        for (ContractApproval a : all) if (staleAndInvalidated(tenantId, a)) approvalRepo.save(a);
        return all;
    }

    @Transactional(readOnly = true)
    public List<ContractApprovalEvent> events(TenantId tenantId, UUID approvalId) {
        approvalRepo.findOne(tenantId.getValue(), approvalId).orElseThrow(() -> new ResourceNotFoundException("Approval", approvalId.toString()));
        return eventRepo.findByApproval(tenantId.getValue(), approvalId);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void requireApprover(ContractApproval a, Collection<String> authorities) {
        if (!a.getAction().canBeDecidedBy(authorities))
            throw new org.springframework.security.access.AccessDeniedException("Your role cannot decide this kind of approval");
    }

    private boolean staleAndInvalidated(TenantId tenantId, ContractApproval a) {
        if (!a.isOpen()) return false;
        Optional<Subject> s = subjectIfPresent(tenantId, a.getAction(), a.getSubjectId());
        if (s.isPresent() && s.get().hash().equals(a.getSubjectHash())) return false;
        invalidate(a, s.isPresent() ? "Changed since it was requested" : "The item no longer exists");
        return true;
    }

    private void invalidate(ContractApproval a, String why) {
        a.invalidate();
        event(a, "INVALIDATED", null, why);
    }

    private void event(ContractApproval a, String type, UUID actor, String detail) {
        eventRepo.save(ContractApprovalEvent.of(a.getTenantId(), a.getId(), type, actor, detail));
    }

    private record Subject(String label, String hash) {}

    private Subject subject(TenantId tenantId, ApprovalAction action, UUID id) {
        return subjectIfPresent(tenantId, action, id).orElseThrow(() -> new ResourceNotFoundException(action.subjectType(), id.toString()));
    }

    private Optional<Subject> subjectIfPresent(TenantId tenantId, ApprovalAction action, UUID id) {
        switch (action.subjectType()) {
            case "CONTRACT": {
                return contractRepo.findActiveById(tenantId, id).map(c -> {
                    List<String> parts = new ArrayList<>();
                    // terminating depends on the contract's state only; sending depends on everything the recipient will be asked to sign
                    parts.add(c.getId().toString());
                    if (action == ApprovalAction.SEND_FOR_SIGNING) {
                        parts.addAll(List.of(nz(c.getTitle()), nz(c.getContractType()), nz(c.getBody()), String.valueOf(c.getValueAmount()),
                                nz(c.getCurrency()), String.valueOf(c.getStartDate()), String.valueOf(c.getEndDate()),
                                String.valueOf(c.getTemplateId()), String.valueOf(c.isAutoRenew()), String.valueOf(c.getRenewalNoticeDays())));
                        partyRepo.findByContract(c.getId()).stream()
                                .map(p -> String.join("|", nz(p.getFullName()), nz(p.getEmail()), nz(p.getPhone()), nz(p.getIdNumber()), nz(p.getCompanyName()),
                                        nz(p.getPartyRole()), nz(p.getPartyType()), String.valueOf(p.getSigningOrder())))
                                .sorted().forEach(parts::add);
                    } else {
                        parts.add(nz(c.getStatus()));
                    }
                    return new Subject(c.getContractNumber() + " — " + nz(c.getTitle()), sha256(parts));
                });
            }
            case "CLAUSE":
                return clauseRepo.findByTenantIdAndId(tenantId.getValue(), id).map(c -> new Subject(c.getTitle() + " v" + c.getVersionNo(),
                        sha256(List.of(c.getId().toString(), nz(c.getTitle()), nz(c.getCategory()), nz(c.getBody()), nz(c.getLegalArea()), nz(c.getJurisdiction()),
                                nz(c.getClauseKind()), nz(c.getSourceReference()), String.valueOf(c.getEvidenceDocumentId()), String.valueOf(c.getReviewDate()),
                                nz(c.getReviewerName()), nz(c.getReviewNotes())))));
            case "TEMPLATE":
                return templateRepo.findActiveById(tenantId, id).map(t -> new Subject(t.getName() + " v" + t.getTemplateVersion(),
                        sha256(List.of(t.getId().toString(), nz(t.getName()), nz(t.getContractType()), nz(t.getDescription()), nz(t.getBodyTemplate()),
                                nz(t.getLegalArea()), nz(t.getRequiredSigningMethod()), String.valueOf(t.getTemplateVersion())))));
            case "PROPOSAL":
                return proposalRepo.findByTenantIdAndId(tenantId.getValue(), id).map(p -> {
                    String number = contractRepo.findActiveById(tenantId, p.getContractId()).map(Contract::getContractNumber).orElse("contract");
                    return new Subject("Proposed change on " + number,
                            sha256(List.of(p.getId().toString(), p.getContractId().toString(), nz(p.getKind()), String.valueOf(p.getBlockIndex()),
                                    nz(p.getOriginalHtml()), nz(p.getProposedHtml()), nz(p.getRationale()))));
                });
            case "PARTY":
                return partyRepo.findByTenantAndId(tenantId, id).flatMap(party ->
                        contractRepo.findActiveById(tenantId, party.getContractId()).map(c ->
                                new Subject(c.getContractNumber() + " — signing for " + nz(party.getFullName()),
                                        sha256(List.of(party.getId().toString(), c.getId().toString(), nz(c.getBodyHash()), nz(party.getFullName()),
                                                nz(party.getEmail()), nz(party.getIdNumber())))))));
            default:
                return Optional.empty();
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    static String sha256(List<String> parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                byte[] b = p.getBytes(StandardCharsets.UTF_8);
                md.update((b.length + ":").getBytes(StandardCharsets.UTF_8));   // length-prefixed so field boundaries cannot be shifted
                md.update(b);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
