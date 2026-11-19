package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.RegulatoryLibraryDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The regulatory library: instruments -> versions -> requirements.
 * <ul>
 *  <li>Platform rows (tenant_id NULL) are readable by everyone and editable by no tenant; a tenant ADOPTS a copy.</li>
 *  <li>A published version is immutable. An amendment is a new version, so history is never rewritten.</li>
 *  <li>Publishing needs an effective date and at least one requirement, and is serialised per instrument by a row lock.</li>
 *  <li>Every change is written to the append-only regulatory event trail.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RegulatoryLibraryService {

    private static final String INSTRUMENT = "INSTRUMENT";
    private static final String VERSION = "VERSION";
    private static final String REQUIREMENT = "REQUIREMENT";

    private final RegulatoryInstrumentRepository instruments;
    private final RegulatoryVersionRepository versions;
    private final RegulatoryRequirementRepository requirements;
    private final RegulatoryEventRepository events;
    private final RegulatoryAuditService audit;
    private final RegulatoryChangeService changeService;   // chunk 2: change detection runs in the publish/repeal transaction

    // ── Instruments ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<InstrumentResponse> search(TenantId tenantId, String scope, ObligationCategory topic, String q, Pageable pageable) {
        String s = scope == null ? "ALL" : scope.toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "PLATFORM", "MINE").contains(s)) throw bad("scope must be ALL, PLATFORM or MINE");
        String like = q == null || q.isBlank() ? "%" : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return instruments.search(tenantId.getValue(), s, topic, like, pageable).map(InstrumentResponse::of);
    }

    @Transactional(readOnly = true)
    public InstrumentDetailResponse detail(TenantId tenantId, UUID id) {
        RegulatoryInstrument i = visibleInstrument(tenantId, id);
        List<VersionResponse> vs = versions.findByInstrument(id).stream()
                .map(v -> VersionResponse.of(v, requirements.countByVersion(v.getId()))).toList();
        return new InstrumentDetailResponse(InstrumentResponse.of(i), vs);
    }

    @Transactional
    public InstrumentResponse create(TenantId tenantId, UUID userId, String userName, InstrumentRequest req) {
        UUID tid = tenantId.getValue();
        requireUniqueTitle(tid, req.title(), null);
        RegulatoryInstrument i = RegulatoryInstrument.create(tid, req.identifier().trim(), req.title().trim(), req.instrumentType(),
                req.jurisdictionLevel(), req.authority(), req.topic(), req.sourceUrl(), req.description(), null, userId);
        instruments.save(i);
        audit.record(tid, INSTRUMENT, i.getId(), "CREATED", i.getTitle(), userId, userName);
        return InstrumentResponse.of(i);
    }

    @Transactional
    public InstrumentResponse update(TenantId tenantId, UUID id, UUID userId, String userName, InstrumentRequest req) {
        UUID tid = tenantId.getValue();
        RegulatoryInstrument i = ownedInstrument(tid, id);
        requireUniqueTitle(tid, req.title(), id);
        i.update(req.identifier().trim(), req.title().trim(), req.instrumentType(), req.jurisdictionLevel(), req.authority(),
                req.topic(), req.sourceUrl(), req.description());
        instruments.save(i);
        audit.record(tid, INSTRUMENT, id, "UPDATED", "Details changed; review status reset", userId, userName);
        return InstrumentResponse.of(i);
    }

    /**
     * Copy a platform catalogue entry into this tenant's own library (idempotent). If the platform entry already
     * has a CURRENT version with requirements, they are copied into a DRAFT so the tenant can review and publish.
     */
    @Transactional
    public InstrumentResponse adopt(TenantId tenantId, UUID platformInstrumentId, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        RegulatoryInstrument src = instruments.findVisible(tid, platformInstrumentId).orElseThrow(() -> notFound("Instrument", platformInstrumentId));
        if (!src.isPlatform()) throw new HandyFlowException("Only catalogue entries can be adopted", HttpStatus.BAD_REQUEST, "NOT_PLATFORM");

        var existing = instruments.findAdopted(tid, platformInstrumentId);
        if (existing.isPresent()) return InstrumentResponse.of(existing.get());
        requireUniqueTitle(tid, src.getTitle(), null);

        RegulatoryInstrument copy = RegulatoryInstrument.create(tid, src.getIdentifier(), src.getTitle(), src.getInstrumentType(),
                src.getJurisdictionLevel(), src.getAuthority(), src.getTopic(), src.getSourceUrl(), src.getDescription(),
                src.getId(), userId);
        instruments.save(copy);

        var current = versions.findByInstrumentAndStatus(src.getId(), VersionStatus.CURRENT);
        if (current.isPresent()) {
            RegulatoryVersion cv = current.get();
            RegulatoryVersion draft = RegulatoryVersion.createDraft(tid, copy.getId(), cv.getVersionLabel(), cv.getEffectiveDate(),
                    cv.getAmendedDate(), cv.getSourceUrl(), cv.getSummary(), userId);
            versions.save(draft);
            for (RegulatoryRequirement r : requirements.findByVersion(cv.getId())) {
                requirements.save(RegulatoryRequirement.create(tid, copy.getId(), draft.getId(), r.getReference(), r.getTitle(),
                        r.getRequirementText(), r.getCategory(), r.getRecurrence(), r.getMinEmployees(), r.getIndustries(),
                        r.getActivities(), r.getEvidenceExpectation(), null, userId));
            }
        }
        audit.record(tid, INSTRUMENT, copy.getId(), "ADOPTED", "Adopted from catalogue entry " + src.getTitle(), userId, userName);
        return InstrumentResponse.of(copy);
    }

    @Transactional
    public InstrumentResponse markReviewed(TenantId tenantId, UUID id, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        RegulatoryInstrument i = ownedInstrument(tid, id);
        i.markReviewed(userId, userName);
        instruments.save(i);
        audit.record(tid, INSTRUMENT, id, "REVIEWED", "Content reviewed by " + userName, userId, userName);
        return InstrumentResponse.of(i);
    }

    // ── Versions ──────────────────────────────────────────────────────────────

    @Transactional
    public VersionResponse createVersion(TenantId tenantId, UUID instrumentId, UUID userId, String userName, VersionRequest req) {
        UUID tid = tenantId.getValue();
        RegulatoryInstrument i = ownedInstrument(tid, instrumentId);
        if (versions.findByInstrumentAndStatus(instrumentId, VersionStatus.DRAFT).isPresent())
            throw conflict("DRAFT_EXISTS", "This instrument already has a draft version — publish or edit it first");

        RegulatoryVersion draft = RegulatoryVersion.createDraft(tid, i.getId(), req.versionLabel().trim(), req.effectiveDate(),
                req.amendedDate(), req.sourceUrl(), req.summary(), userId);
        versions.save(draft);

        long copied = 0;
        if (req.carryForwardFromVersionId() != null) {
            RegulatoryVersion from = versions.findVisible(tid, req.carryForwardFromVersionId()).orElseThrow(() -> notFound("Version", req.carryForwardFromVersionId()));
            if (!from.getInstrumentId().equals(instrumentId)) throw bad("The version to carry forward from belongs to another instrument");
            for (RegulatoryRequirement r : requirements.findByVersion(from.getId())) {
                requirements.save(r.carryForwardTo(draft.getId(), userId));
                copied++;
            }
        }
        audit.record(tid, VERSION, draft.getId(), "DRAFT_CREATED",
                req.versionLabel().trim() + (copied > 0 ? " (" + copied + " requirement(s) carried forward)" : ""), userId, userName);
        return VersionResponse.of(draft, copied);
    }

    @Transactional
    public VersionResponse updateVersion(TenantId tenantId, UUID versionId, UUID userId, String userName, VersionRequest req) {
        RegulatoryVersion v = ownedVersion(tenantId.getValue(), versionId);
        requireDraft(v);
        v.editDraft(req.versionLabel().trim(), req.effectiveDate(), req.amendedDate(), req.sourceUrl(), req.summary());
        versions.save(v);
        audit.record(tenantId.getValue(), VERSION, versionId, "DRAFT_UPDATED", v.getVersionLabel(), userId, userName);
        return VersionResponse.of(v, requirements.countByVersion(versionId));
    }

    @Transactional
    public VersionResponse publish(TenantId tenantId, UUID versionId, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        RegulatoryVersion v = ownedVersion(tid, versionId);
        requireDraft(v);
        instruments.lockOwned(tid, v.getInstrumentId()).orElseThrow(() -> notFound("Instrument", v.getInstrumentId()));
        if (v.getEffectiveDate() == null) throw bad("An effective date is required before publishing");
        long count = requirements.countByVersion(versionId);
        if (count == 0) throw bad("A version needs at least one requirement before it can be published");

        RegulatoryVersion previous = versions.findByInstrumentAndStatus(v.getInstrumentId(), VersionStatus.CURRENT).orElse(null);
        if (previous != null) {
            RegulatoryVersion prev = previous;
            prev.supersede();
            versions.saveAndFlush(prev);      // free the one-CURRENT slot before promoting the draft
            audit.record(tid, VERSION, prev.getId(), "SUPERSEDED", "Superseded by " + v.getVersionLabel(), userId, userName);
        }
        v.publish(userId, userName);
        versions.saveAndFlush(v);
        audit.record(tid, VERSION, versionId, "PUBLISHED", v.getVersionLabel() + " effective " + v.getEffectiveDate()
                + " with " + count + " requirement(s)", userId, userName);
        RegulatoryInstrument instrument = instruments.findOwned(tid, v.getInstrumentId()).orElseThrow(() -> notFound("Instrument", v.getInstrumentId()));
        changeService.onPublished(tenantId, instrument, previous, v, userId, userName);
        return VersionResponse.of(v, count);
    }

    @Transactional
    public VersionResponse repeal(TenantId tenantId, UUID versionId, RepealRequest req, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        RegulatoryVersion v = ownedVersion(tid, versionId);
        boolean wasCurrent = v.getStatus() == VersionStatus.CURRENT;
        try {
            v.repeal(req.repealedDate());
        } catch (IllegalStateException e) {
            throw conflict("INVALID_STATE", e.getMessage());
        }
        versions.save(v);
        audit.record(tid, VERSION, versionId, "REPEALED", req.reason().trim(), userId, userName);
        if (wasCurrent) {      // repealing a superseded version changes nothing for anyone
            RegulatoryInstrument instrument = instruments.findOwned(tid, v.getInstrumentId()).orElseThrow(() -> notFound("Instrument", v.getInstrumentId()));
            changeService.onRepealed(tenantId, instrument, v, req.reason().trim(), userId, userName);
        }
        return VersionResponse.of(v, requirements.countByVersion(versionId));
    }

    // ── Requirements ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RequirementResponse> listRequirements(TenantId tenantId, UUID versionId) {
        versions.findVisible(tenantId.getValue(), versionId).orElseThrow(() -> notFound("Version", versionId));
        return requirements.findByVersion(versionId).stream().map(RequirementResponse::of).toList();
    }

    @Transactional
    public RequirementResponse addRequirement(TenantId tenantId, UUID versionId, UUID userId, String userName, RequirementRequest req) {
        UUID tid = tenantId.getValue();
        RegulatoryVersion v = ownedVersion(tid, versionId);
        requireDraft(v);
        requireFreeReference(versionId, req.reference(), new UUID(0, 0));
        RegulatoryRequirement r = RegulatoryRequirement.create(tid, v.getInstrumentId(), versionId, req.reference().trim(), req.title().trim(),
                req.requirementText().trim(), req.category(), req.recurrence(), req.minEmployees(), req.industries(), req.activities(),
                req.evidenceExpectation(), null, userId);
        requirements.save(r);
        audit.record(tid, REQUIREMENT, r.getId(), "CREATED", r.getReference() + " " + r.getTitle(), userId, userName);
        return RequirementResponse.of(r);
    }

    @Transactional
    public RequirementResponse updateRequirement(TenantId tenantId, UUID requirementId, UUID userId, String userName, RequirementRequest req) {
        UUID tid = tenantId.getValue();
        RegulatoryRequirement r = requirements.findOwned(tid, requirementId).orElseThrow(() -> notFound("Requirement", requirementId));
        requireDraft(ownedVersion(tid, r.getVersionId()));
        requireFreeReference(r.getVersionId(), req.reference(), requirementId);
        r.update(req.reference().trim(), req.title().trim(), req.requirementText().trim(), req.category(), req.recurrence(),
                req.minEmployees(), req.industries(), req.activities(), req.evidenceExpectation());
        requirements.save(r);
        audit.record(tid, REQUIREMENT, requirementId, "UPDATED", r.getReference(), userId, userName);
        return RequirementResponse.of(r);
    }

    @Transactional
    public void deleteRequirement(TenantId tenantId, UUID requirementId, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        RegulatoryRequirement r = requirements.findOwned(tid, requirementId).orElseThrow(() -> notFound("Requirement", requirementId));
        requireDraft(ownedVersion(tid, r.getVersionId()));
        requirements.delete(r);
        audit.record(tid, REQUIREMENT, requirementId, "DELETED", r.getReference() + " removed from draft", userId, userName);
    }

    // ── Audit ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EventResponse> events(TenantId tenantId, String entityType, UUID entityId) {
        return events.forEntity(tenantId.getValue(), entityType.toUpperCase(Locale.ROOT), entityId).stream().map(EventResponse::of).toList();
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private RegulatoryInstrument visibleInstrument(TenantId t, UUID id) {
        return instruments.findVisible(t.getValue(), id).orElseThrow(() -> notFound("Instrument", id));
    }

    /** Platform rows are visible but never editable — a tenant gets 409, not a silent no-op. */
    private RegulatoryInstrument ownedInstrument(UUID tid, UUID id) {
        return instruments.findOwned(tid, id).orElseGet(() -> {
            if (instruments.findVisible(tid, id).isPresent())
                throw conflict("PLATFORM_READ_ONLY", "Catalogue entries are read-only — adopt a copy to edit it");
            throw notFound("Instrument", id);
        });
    }

    private RegulatoryVersion ownedVersion(UUID tid, UUID id) {
        return versions.findOwned(tid, id).orElseGet(() -> {
            if (versions.findVisible(tid, id).isPresent())
                throw conflict("PLATFORM_READ_ONLY", "Catalogue versions are read-only — adopt a copy to edit it");
            throw notFound("Version", id);
        });
    }

    private void requireDraft(RegulatoryVersion v) {
        if (!v.isDraft())
            throw conflict("VERSION_IMMUTABLE", "A published version cannot be changed — create a new version for an amendment");
    }

    private void requireUniqueTitle(UUID tid, String title, UUID excludeId) {
        instruments.findByTitle(tid, title.trim()).filter(x -> !x.getId().equals(excludeId)).ifPresent(x -> {
            throw conflict("DUPLICATE_INSTRUMENT", "You already have an instrument titled '" + title.trim() + "'");
        });
    }

    private void requireFreeReference(UUID versionId, String reference, UUID excludeId) {
        if (requirements.referenceTaken(versionId, reference.trim(), excludeId))
            throw conflict("DUPLICATE_REFERENCE", "Reference '" + reference.trim() + "' is already used in this version");
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "VALIDATION"); }
    private static HandyFlowException conflict(String code, String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }
    private static ResourceNotFoundException notFound(String what, UUID id) { return new ResourceNotFoundException(what, id.toString()); }
}
