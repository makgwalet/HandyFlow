package za.co.handyflow.platform.approvals.application;

import za.co.handyflow.platform.approvals.domain.model.ApprovalRule;
import za.co.handyflow.platform.approvals.dto.ChainEntryInput;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The public contract other modules depend on — same shape as
 * EvidenceFacade/ControlExceptionFacade (a narrow interface here, the
 * real implementation kept in application/internal, so a calling
 * module never depends on this module's internal service class
 * directly).
 * <p>
 * Rule management (create/update/deactivate/list ApprovalRule) is
 * deliberately NOT part of this contract — that's this module's own
 * tenant-facing configuration surface (ApprovalRuleController calling
 * ApprovalEngineService directly, same-module, no facade indirection
 * needed), not something another module submitting/acting on approvals
 * needs to touch.
 */
public interface ApprovalFacade {

    /**
     * Submit an entity for approval. metadata is whatever fields the
     * calling module's rules need to evaluate against (e.g.
     * {"totalAmount": 15000}) — a flat map of simple values (numbers,
     * strings, booleans), not the entity itself.
     * <p>
     * If no matching rule exists for this module+entityType (neither a
     * tenant-specific one nor a platform default), the request is
     * auto-approved immediately and returned already in a terminal
     * state — "no rule configured" never silently means "pending
     * forever," it means "nothing gates this yet."
     */
    ApprovalRequestResponse submit(TenantId tenantId, String module, String entityType,
                                   UUID entityId, UUID submittedBy, Map<String, Object> metadata);

    /**
     * FIX: backlog 1.1 (Creative migration) — for callers whose approver
     * chain is chosen per-submission by a real person, not matched from
     * a tenant-wide condition-based rule (see ChainEntryInput's own
     * Javadoc for why AP's rule-matching model doesn't fit this case).
     * Bypasses rule-matching entirely — the caller already knows exactly
     * who needs to approve. ApprovalRequest.ruleId is null for requests
     * created this way.
     * <p>
     * ApprovalRule.ApprovalMode is reused directly here rather than a
     * duplicate ad-hoc-only enum meaning the same three values — a small,
     * acknowledged coupling to the rule entity's nested type, preferred
     * over maintaining two enums for one concept.
     */
    ApprovalRequestResponse submitAdHoc(TenantId tenantId, String module, String entityType,
                                        UUID entityId, UUID submittedBy,
                                        ApprovalRule.ApprovalMode mode,
                                        List<ChainEntryInput> approverChain,
                                        Map<String, Object> metadata);

    /**
     * Resubmit after a RETURNED_FOR_CORRECTION outcome — creates a NEW
     * ApprovalRequest linked via resubmittedFromId, and marks the
     * original RESUBMITTED. Re-evaluates rules against fresh metadata
     * (the corrected entity may now match a different rule than the
     * original submission did).
     */
    ApprovalRequestResponse resubmit(TenantId tenantId, UUID originalRequestId,
                                     UUID submittedBy, Map<String, Object> metadata);

    /**
     * @param decision "APPROVE" or "REJECT"
     * @param actingUserAuthorities the acting user's own JWT authorities,
     *        resolved by the CALLING controller from SecurityContextHolder
     *        — this module never queries identity for role membership
     *        itself (see package-info.java).
     */
    ApprovalRequestResponse actOnStep(TenantId tenantId, UUID stepId, UUID actingUserId,
                                      List<String> actingUserAuthorities,
                                      String decision, String comment, String actorIp);

    /**
     * FIX: backlog 1.1 (Creative migration) — the EXTERNAL_CONTACT
     * counterpart to actOnStep(). No actingUserId/authorities — the
     * token itself is the credential. Enforces the exact same
     * SEQUENTIAL-ordering rule as the internal path.
     */
    ApprovalRequestResponse actOnPublicStep(String publicToken, String decision,
                                            String comment, String actorIp);

    /**
     * FIX: backlog 1.1 (Creative migration) — resolves a public token to
     * its FULL parent request (not just the isolated step) — Creative
     * needs the request's entityId to look up the CreProof itself, which
     * an isolated ApprovalStepResponse alone can't provide. Entirely
     * tenant-agnostic, same as the token itself — the caller doesn't
     * know which tenant a token belongs to until after resolving it.
     * Returns empty for an unknown token; the caller decides how to
     * phrase that to the client.
     */
    Optional<ApprovalRequestResponse> getRequestByStepToken(String publicToken);

    Optional<ApprovalRequestResponse> getLatestRequestForEntity(TenantId tenantId, String module,
                                                                String entityType, UUID entityId);

    List<ApprovalStepResponse> getMyPendingSteps(TenantId tenantId, UUID userId, List<String> authorities);
}