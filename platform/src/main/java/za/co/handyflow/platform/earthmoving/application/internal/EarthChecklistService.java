package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthChecklistQuestionRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthChecklistVersionRepository;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

/**
 * A company's own inspection checklists. Administrators write a draft for an inspection type, publish it from a date, and from then on
 * inspections of that type are answered against it. A published version is never edited; a change is a new version, so an old inspection
 * always shows the questions that were asked. A company with nothing published uses the built-in walk-round.
 */
@Service
@RequiredArgsConstructor
public class EarthChecklistService {

    /** The order the screen lists the types in. */
    static final List<String> TYPES = List.of(InspectionRules.PRE_USE, InspectionRules.HANDOVER, InspectionRules.RETURN, InspectionRules.MAINTENANCE, InspectionRules.SAFETY);

    private final EarthChecklistVersionRepository versions;
    private final EarthChecklistQuestionRepository questions;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    @Transactional(readOnly = true)
    public ActiveChecklistResponse active(TenantId tenantId, String type) {
        access.require(EarthmovingRules.READ, "view checklists");
        return toActive(inForce(tenantId, InspectionRules.type(type)), type);
    }

    @Transactional(readOnly = true)
    public List<ChecklistTypeOverview> overview(TenantId tenantId) {
        access.require(EarthmovingRules.READ, "view checklists");
        List<ChecklistTypeOverview> out = new ArrayList<>();
        for (String type : TYPES) {
            List<EarthChecklistVersion> all = versions.findByType(tenantId, type);
            EarthChecklistVersion draft = all.stream().filter(EarthChecklistVersion::isDraft).findFirst().orElse(null);
            List<ChecklistTypeOverview.Past> history = all.stream().filter(v -> !v.isDraft()).map(v ->
                    new ChecklistTypeOverview.Past(v.getId(), v.getVersionNo(), v.getStatus(), v.getEffectiveFrom(), v.getPublishedAt(),
                            questions.findForVersion(tenantId, v.getId()).size())).toList();
            ActiveChecklistResponse active = toActive(inForce(tenantId, type), type);
            out.add(new ChecklistTypeOverview(type, active, draft == null ? null : toVersion(tenantId, draft), history));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ChecklistVersionResponse version(TenantId tenantId, UUID id) {
        access.require(EarthmovingRules.READ, "view checklists");
        return toVersion(tenantId, owned(tenantId, id));
    }

    /** Starts a draft from what is in force now (or the built-in list), so a change begins from the current questions. */
    @Transactional
    public ChecklistVersionResponse createDraft(TenantId tenantId, String typeIn, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "write a checklist");
        String type = InspectionRules.type(typeIn);
        if (versions.findDraft(tenantId, type).isPresent())
            throw new IllegalStateException("There is already a draft " + label(type) + " checklist: finish it or discard it first.");
        ChecklistRules.Active from = resolve(tenantId, type, null);
        EarthChecklistVersion v = versions.save(EarthChecklistVersion.draft(tenantId, type, versions.latestNumber(tenantId, type) + 1, from.requiresSignoff(), actor));
        write(tenantId, v.getId(), from.questions());
        trail.record(tenantId, "EARTHMOVING_CHECKLIST_DRAFTED", "EARTHMOVING_CHECKLIST", v.getId(), null, Map.of("type", type, "version", v.getVersionNo()));
        return toVersion(tenantId, v);
    }

    @Transactional
    public ChecklistVersionResponse saveDraft(TenantId tenantId, UUID id, SaveChecklistDraftRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "write a checklist");
        EarthChecklistVersion v = owned(tenantId, id);
        if (!v.isDraft()) throw new IllegalStateException("A published checklist is never changed: start a new version.");
        List<ChecklistRules.Question> clean = ChecklistRules.clean(v.getInspectionType(), req.questions().stream().map(EarthChecklistService::toRule).toList());
        v.change(req.requiresSignoff(), req.note());
        versions.save(v);
        questions.deleteForVersion(tenantId, v.getId());
        write(tenantId, v.getId(), clean);
        trail.record(tenantId, "EARTHMOVING_CHECKLIST_SAVED", "EARTHMOVING_CHECKLIST", v.getId(), null,
                Map.of("type", v.getInspectionType(), "version", v.getVersionNo(), "questions", clean.size()));
        return toVersion(tenantId, v);
    }

    @Transactional
    public ChecklistVersionResponse publish(TenantId tenantId, UUID id, PublishChecklistRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "publish a checklist");
        EarthChecklistVersion v = owned(tenantId, id);
        if (!v.isDraft()) throw new IllegalStateException("This version is already published.");
        List<ChecklistRules.Question> rows = questions.findForVersion(tenantId, v.getId()).stream().map(EarthChecklistQuestion::toQuestion).toList();
        ChecklistRules.clean(v.getInspectionType(), rows);
        LocalDate from = ChecklistRules.effectiveFrom(req == null ? null : req.effectiveFrom(), EarthHireService.today());
        v.publish(from, actor);
        versions.save(v);
        trail.record(tenantId, "EARTHMOVING_CHECKLIST_PUBLISHED", "EARTHMOVING_CHECKLIST", v.getId(), null,
                Map.of("type", v.getInspectionType(), "version", v.getVersionNo(), "effectiveFrom", from.toString(), "questions", rows.size()));
        return toVersion(tenantId, v);
    }

    @Transactional
    public void discard(TenantId tenantId, UUID id, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "discard a draft checklist");
        EarthChecklistVersion v = owned(tenantId, id);
        if (!v.isDraft()) throw new IllegalStateException("A published checklist cannot be removed: inspections were answered against it.");
        questions.deleteForVersion(tenantId, v.getId());
        versions.delete(v);
        trail.record(tenantId, "EARTHMOVING_CHECKLIST_DISCARDED", "EARTHMOVING_CHECKLIST", v.getId(), Map.of("type", v.getInspectionType(), "version", v.getVersionNo()), null);
    }

    /**
     * The checklist an inspection is answered against: the version the person had open when they gave one (it must be a published version of
     * that type that has started), otherwise the one in force, otherwise the built-in list. MAINTENANCE and SAFETY have no built-in list.
     */
    public ChecklistRules.Active resolve(TenantId tenantId, String type, UUID versionId) {
        if (versionId != null) {
            EarthChecklistVersion v = versions.findOwned(tenantId, versionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Checklist version", versionId.toString()));
            if (!v.getInspectionType().equals(type)) throw new IllegalArgumentException("That checklist is for a different kind of inspection.");
            if (v.isDraft() || v.getEffectiveFrom().isAfter(EarthHireService.today()))
                throw new IllegalStateException("That checklist version is not in use yet.");
            return active(tenantId, v);
        }
        ChecklistRules.Active found = inForce(tenantId, type);
        if (found != null) return found;
        if (ChecklistRules.MINIMUM_TYPES.contains(type)) return ChecklistRules.builtInActive(type);
        throw new IllegalStateException("There is no " + label(type) + " checklist yet: an administrator needs to publish one first.");
    }

    private ChecklistRules.Active inForce(TenantId tenantId, String type) {
        List<EarthChecklistVersion> found = versions.findInForce(tenantId, type, EarthHireService.today(), PageRequest.of(0, 1));
        if (!found.isEmpty()) return active(tenantId, found.get(0));
        return ChecklistRules.MINIMUM_TYPES.contains(type) ? ChecklistRules.builtInActive(type) : null;
    }

    private ChecklistRules.Active active(TenantId tenantId, EarthChecklistVersion v) {
        return new ChecklistRules.Active(v.getId(), v.getVersionNo(), v.getInspectionType(), v.isRequiresSignoff(), v.getEffectiveFrom(),
                questions.findForVersion(tenantId, v.getId()).stream().map(EarthChecklistQuestion::toQuestion).toList());
    }

    private void write(TenantId tenantId, UUID versionId, List<ChecklistRules.Question> rows) {
        for (int n = 0; n < rows.size(); n++) questions.save(EarthChecklistQuestion.of(tenantId, versionId, n + 1, rows.get(n)));
    }

    private EarthChecklistVersion owned(TenantId tenantId, UUID id) {
        return versions.findOwned(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Checklist version", id.toString()));
    }

    private static String label(String type) { return type.toLowerCase(Locale.ROOT).replace('_', '-'); }

    private static ChecklistRules.Question toRule(ChecklistQuestionDto d) {
        return new ChecklistRules.Question(d.id(), d.section(), d.text(), d.answerType(), d.required(), d.builtIn(), d.min(), d.max(), d.unit(), d.failOutcome());
    }

    private static ChecklistQuestionDto toDto(ChecklistRules.Question q) {
        return new ChecklistQuestionDto(q.id(), q.section(), q.text(), q.answerType(), q.required(), q.builtIn(), q.min(), q.max(), q.unit(), q.failOutcome());
    }

    private static ActiveChecklistResponse toActive(ChecklistRules.Active a, String type) {
        if (a == null) return null;
        return new ActiveChecklistResponse(a.versionId(), a.versionNo(), a.type(), a.requiresSignoff(), a.effectiveFrom(),
                a.builtInDefault() ? "BUILT_IN" : "COMPANY", a.questions().stream().map(EarthChecklistService::toDto).toList());
    }

    private ChecklistVersionResponse toVersion(TenantId tenantId, EarthChecklistVersion v) {
        return new ChecklistVersionResponse(v.getId(), v.getInspectionType(), v.getVersionNo(), v.getStatus(), v.getEffectiveFrom(), v.isRequiresSignoff(),
                v.getNote(), v.getCreatedAt(), v.getPublishedAt(),
                questions.findForVersion(tenantId, v.getId()).stream().map(q -> toDto(q.toQuestion())).toList());
    }
}
