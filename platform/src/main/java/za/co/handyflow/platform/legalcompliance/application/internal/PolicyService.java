package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.AttestationCampaign;
import za.co.handyflow.platform.legalcompliance.domain.model.Policy;
import za.co.handyflow.platform.legalcompliance.domain.model.PolicyVersion;
import za.co.handyflow.platform.legalcompliance.domain.model.PolicyVersionStatus;
import za.co.handyflow.platform.legalcompliance.domain.repository.AttestationCampaignRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.PolicyRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.PolicyVersionRepository;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.FindingNoteRequest;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.NoteRequest;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.GovernanceDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Policies and their versions. One published version and at most one open version (draft / in review / approved) per policy.
 * Segregation of duties is unconditional: the approver is never the author (whoever last edited the draft counts as the author).
 * Publishing supersedes the previous published version in its own flush first, because the unique "one published" index would otherwise reject the swap,
 * and closes any attestation campaign still open on the old version.
 */
@Service
@RequiredArgsConstructor
public class PolicyService {

    static final String ENTITY = "POLICY";

    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final AttestationCampaignRepository campaigns;
    private final RegulatoryAuditService trail;

    @Transactional
    public PolicyDetailResponse create(TenantId tenantId, PolicyRequest req, UUID userId, String userName) {
        if (req.content() == null || req.content().isBlank()) throw new HandyFlowException("The policy text is required", HttpStatus.BAD_REQUEST, "VALIDATION");
        Policy p = guarded(() -> Policy.create(tenantId, String.format("POL-%05d", policies.nextRef()), req.title(), req.policyType(), req.ownerId(), req.ownerName(),
                req.reviewMonths() == 0 ? 12 : req.reviewMonths(), userId, userName));
        policies.save(p);
        PolicyVersion v = guarded(() -> PolicyVersion.draft(tenantId, p.getId(), 1, req.content(), null, userId, userName));
        versions.save(v);
        trail.record(tenantId.getValue(), ENTITY, p.getId(), "CREATED", p.getReference() + " " + p.getTitle() + " — draft version 1", userId, userName);
        return detail(tenantId, p);
    }

    @Transactional
    public PolicyResponse update(TenantId tenantId, UUID id, PolicyRequest req, UUID userId, String userName) {
        Policy p = load(tenantId, id);
        guarded(() -> { p.update(req.title(), req.policyType(), req.ownerId(), req.ownerName(), req.reviewMonths() == 0 ? p.getReviewMonths() : req.reviewMonths()); return null; });
        policies.save(p);
        trail.record(tenantId.getValue(), ENTITY, id, "UPDATED", "Title, owner or review period changed", userId, userName);
        return view(tenantId, p);
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> list(TenantId tenantId, boolean includeRetired) { return policies.search(tenantId, includeRetired).stream().map(p -> view(tenantId, p)).toList(); }

    @Transactional(readOnly = true)
    public PolicyDetailResponse get(TenantId tenantId, UUID id) { return detail(tenantId, load(tenantId, id)); }

    @Transactional(readOnly = true)
    public PolicySummary summary(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        List<Policy> active = policies.search(tenantId, false);
        long awaiting = 0, drafts = 0;
        for (Policy p : active) {
            var open = versions.findOpen(tenantId, p.getId());
            if (open.isPresent()) { if (open.get().getStatus() == PolicyVersionStatus.DRAFT) drafts++; else awaiting++; }
        }
        return new PolicySummary(active.size(), active.stream().filter(p -> GovernanceRules.reviewOverdue(p.getNextReviewDue(), true, today)).count(),
                active.stream().filter(p -> GovernanceRules.reviewDueSoon(p.getNextReviewDue(), true, today)).count(), awaiting, drafts);
    }

    @Transactional
    public PolicyDetailResponse newVersion(TenantId tenantId, UUID policyId, VersionRequest req, UUID userId, String userName) {
        Policy p = load(tenantId, policyId);
        if (!p.isActive()) throw new HandyFlowException("The policy is retired", HttpStatus.CONFLICT, "INVALID_STATE");
        if (versions.findOpen(tenantId, policyId).isPresent()) throw new HandyFlowException("There is already a version in progress; finish or return it first", HttpStatus.CONFLICT, "VERSION_IN_PROGRESS");
        int number = versions.maxVersion(tenantId, policyId) + 1;
        PolicyVersion v = guarded(() -> PolicyVersion.draft(tenantId, policyId, number, req.content(), req.changeSummary(), userId, userName));
        versions.save(v);
        trail.record(tenantId.getValue(), ENTITY, policyId, "VERSION_DRAFTED", "Draft version " + number, userId, userName);
        return detail(tenantId, p);
    }

    @Transactional
    public PolicyDetailResponse editVersion(TenantId tenantId, UUID versionId, VersionRequest req, UUID userId, String userName) {
        PolicyVersion v = loadVersion(tenantId, versionId);
        guarded(() -> { v.edit(req.content(), req.changeSummary(), userId, userName); return null; });
        versions.save(v);
        trail.record(tenantId.getValue(), ENTITY, v.getPolicyId(), "VERSION_EDITED", "Draft version " + v.getVersionNumber() + " edited", userId, userName);
        return detail(tenantId, load(tenantId, v.getPolicyId()));
    }

    @Transactional
    public PolicyDetailResponse submit(TenantId tenantId, UUID versionId, UUID userId, String userName) {
        PolicyVersion v = loadVersion(tenantId, versionId);
        guarded(() -> { v.submit(); return null; });
        versions.save(v);
        trail.record(tenantId.getValue(), ENTITY, v.getPolicyId(), "VERSION_SUBMITTED", "Version " + v.getVersionNumber() + " submitted, hash " + v.getContentHash(), userId, userName);
        return detail(tenantId, load(tenantId, v.getPolicyId()));
    }

    @Transactional
    public PolicyDetailResponse approve(TenantId tenantId, UUID versionId, NoteRequest req, UUID userId, String userName) {
        PolicyVersion v = loadVersion(tenantId, versionId);
        requireNotAuthor(v, userId);
        guarded(() -> { v.approve(userId, userName, req == null ? null : req.note()); return null; });
        versions.save(v);
        trail.record(tenantId.getValue(), ENTITY, v.getPolicyId(), "VERSION_APPROVED", "Version " + v.getVersionNumber() + (req == null || req.note() == null ? "" : ": " + req.note().trim()), userId, userName);
        return detail(tenantId, load(tenantId, v.getPolicyId()));
    }

    @Transactional
    public PolicyDetailResponse returnForRework(TenantId tenantId, UUID versionId, FindingNoteRequest req, UUID userId, String userName) {
        PolicyVersion v = loadVersion(tenantId, versionId);
        requireNotAuthor(v, userId);
        guarded(() -> { v.returnForRework(userId, userName, req.note()); return null; });
        versions.save(v);
        trail.record(tenantId.getValue(), ENTITY, v.getPolicyId(), "VERSION_RETURNED", "Version " + v.getVersionNumber() + ": " + req.note().trim(), userId, userName);
        return detail(tenantId, load(tenantId, v.getPolicyId()));
    }

    @Transactional
    public PolicyDetailResponse publish(TenantId tenantId, UUID versionId, PublishRequest req, UUID userId, String userName) {
        PolicyVersion v = loadVersion(tenantId, versionId);
        Policy p = load(tenantId, v.getPolicyId());
        if (v.getStatus() != PolicyVersionStatus.APPROVED) throw new HandyFlowException("Only an approved version can be published — current: " + v.getStatus(), HttpStatus.CONFLICT, "INVALID_STATE");
        LocalDate effective = req == null || req.effectiveDate() == null ? LocalDate.now() : req.effectiveDate();
        var previous = versions.findPublished(tenantId, p.getId());
        if (previous.isPresent()) {
            PolicyVersion old = previous.get();
            guarded(() -> { old.supersede(); return null; });
            versions.saveAndFlush(old);
            for (AttestationCampaign c : campaigns.findOpenForPolicy(tenantId, p.getId()))
                if (c.getPolicyVersionId().equals(old.getId())) { c.close(userName, "Superseded by version " + v.getVersionNumber()); campaigns.save(c); }
        }
        guarded(() -> { v.publish(userName, effective); p.markPublished(v.getId(), effective); return null; });
        versions.save(v);
        policies.save(p);
        trail.record(tenantId.getValue(), ENTITY, p.getId(), "VERSION_PUBLISHED", "Version " + v.getVersionNumber() + " effective " + effective + "; next review " + p.getNextReviewDue(), userId, userName);
        return detail(tenantId, p);
    }

    @Transactional
    public PolicyResponse retire(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, String userName) {
        Policy p = load(tenantId, id);
        guarded(() -> { p.retire(req.reason()); return null; });
        for (AttestationCampaign c : campaigns.findOpenForPolicy(tenantId, id)) { c.close(userName, "Policy retired"); campaigns.save(c); }
        policies.save(p);
        trail.record(tenantId.getValue(), ENTITY, id, "RETIRED", req.reason().trim(), userId, userName);
        return view(tenantId, p);
    }

    // ── internals ──

    Policy load(TenantId tenantId, UUID id) { return policies.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Policy", id.toString())); }

    private PolicyVersion loadVersion(TenantId tenantId, UUID id) { return versions.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Policy version", id.toString())); }

    private static void requireNotAuthor(PolicyVersion v, UUID userId) {
        if (userId != null && userId.equals(v.getAuthorId()))
            throw new HandyFlowException("Segregation of duties: you wrote this version, so you cannot approve or return it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
    }

    private PolicyDetailResponse detail(TenantId tenantId, Policy p) {
        return new PolicyDetailResponse(view(tenantId, p), versions.findByPolicy(tenantId, p.getId()).stream().map(v -> VersionResponse.of(v, true)).toList());
    }

    private PolicyResponse view(TenantId tenantId, Policy p) {
        LocalDate today = LocalDate.now();
        PolicyVersion published = p.getPublishedVersionId() == null ? null : versions.findPublished(tenantId, p.getId()).orElse(null);
        PolicyVersion open = versions.findOpen(tenantId, p.getId()).orElse(null);
        return new PolicyResponse(p.getId(), p.getReference(), p.getTitle(), p.getPolicyType(), p.getOwnerName(), p.getReviewMonths(), p.getStatus(),
                published == null ? null : published.getVersionNumber(), published == null ? null : published.getEffectiveDate(), p.getNextReviewDue(),
                GovernanceRules.reviewOverdue(p.getNextReviewDue(), p.isActive(), today), GovernanceRules.reviewDueSoon(p.getNextReviewDue(), p.isActive(), today),
                open == null ? null : open.getStatus(), open == null ? null : open.getVersionNumber(), p.getRetiredReason(), campaigns.findOpenForPolicy(tenantId, p.getId()).size());
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
