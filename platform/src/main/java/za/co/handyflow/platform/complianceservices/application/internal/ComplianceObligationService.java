package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ObligationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The obligation catalogue. Nothing in it is built in: an obligation exists because someone wrote it down,
 * and it applies only once a second person has approved a dated version of it.
 */
@Service
@RequiredArgsConstructor
public class ComplianceObligationService {

    private final ComplianceObligationRepository obligations;
    private final ComplianceObligationVersionRepository versions;
    private final ComplianceObligationConditionRepository conditions;
    private final ComplianceObligationEvidenceRepository evidence;

    @Transactional(readOnly = true)
    public List<ObligationSummary> list(TenantId tenantId, LocalDate asOf) {
        Map<UUID, List<ComplianceObligationVersion>> byObligation = versions.findAllForTenant(tenantId).stream()
                .collect(Collectors.groupingBy(ComplianceObligationVersion::getObligationId));
        return obligations.findAllForTenant(tenantId).stream().map(o -> {
            List<ComplianceObligationVersion> vs = byObligation.getOrDefault(o.getId(), List.of());
            ComplianceObligationVersion latest = vs.stream().max(Comparator.comparingInt(ComplianceObligationVersion::getVersionNumber)).orElse(null);
            Integer inForce = ObligationRules.effectiveOn(vs.stream().map(ComplianceObligationVersion::span).toList(), asOf);
            ComplianceObligationVersion shown = inForce == null ? latest
                    : vs.stream().filter(v -> v.getVersionNumber() == inForce).findFirst().orElse(latest);
            return new ObligationSummary(o.getId(), o.getCode(), shown == null ? null : shown.getName(), inForce,
                    latest == null ? 0 : latest.getVersionNumber(), latest == null ? null : latest.getReviewStatus(),
                    shown == null ? null : shown.getRiskLevel(), shown == null ? null : shown.getFrequency(),
                    shown == null ? null : shown.getRegulator());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ObligationDetail get(TenantId tenantId, UUID id) {
        return detail(tenantId, find(tenantId, id));
    }

    @Transactional
    public ObligationDetail create(TenantId tenantId, CreateObligationRequest r, UUID by) {
        String code;
        try {
            code = ObligationRules.code(r.code());
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        if (obligations.findByCode(tenantId, code).isPresent()) {
            throw new HandyFlowException("An obligation with code " + code + " already exists", HttpStatus.CONFLICT, "OBLIGATION_CODE_EXISTS");
        }
        ComplianceObligation o = obligations.save(ComplianceObligation.create(tenantId, code, by));
        ComplianceObligationVersion v = ComplianceObligationVersion.draft(tenantId, o.getId(), 1, by);
        fill(tenantId, v, r.version() == null ? emptyRequest() : r.version(), by);
        versions.save(v);
        saveChildren(tenantId, v.getId(), r.version() == null ? emptyRequest() : r.version());
        return detail(tenantId, o);
    }

    @Transactional
    public ObligationDetail saveDraft(TenantId tenantId, UUID obligationId, UUID versionId, SaveVersionRequest r, UUID by) {
        ComplianceObligation o = find(tenantId, obligationId);
        ComplianceObligationVersion v = findVersion(tenantId, obligationId, versionId);
        fill(tenantId, v, r, by);
        versions.save(v);
        saveChildren(tenantId, v.getId(), r);
        return detail(tenantId, o);
    }

    /** Starts the next version as a copy of the latest approved one, so changes are made on a new record. */
    @Transactional
    public ObligationDetail newVersion(TenantId tenantId, UUID obligationId, UUID by) {
        ComplianceObligation o = find(tenantId, obligationId);
        List<ComplianceObligationVersion> vs = versions.findForObligation(tenantId, obligationId);
        if (vs.stream().anyMatch(v -> !v.isApproved())) {
            throw new HandyFlowException("There is already a draft or a version in review", HttpStatus.CONFLICT, "OBLIGATION_DRAFT_EXISTS");
        }
        ComplianceObligationVersion last = vs.stream().max(Comparator.comparingInt(ComplianceObligationVersion::getVersionNumber))
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceObligationVersion", obligationId.toString()));
        ComplianceObligationVersion next = ComplianceObligationVersion.draft(tenantId, obligationId, last.getVersionNumber() + 1, by);
        next.copyFrom(last, by);
        versions.save(next);
        List<UUID> ids = List.of(last.getId());
        int i = 0;
        for (ComplianceObligationCondition c : conditions.findForVersions(tenantId, ids)) {
            conditions.save(ComplianceObligationCondition.of(tenantId, next.getId(), new ObligationRules.Condition(c.getFieldName(), c.getOperator(), c.getValue()), i++));
        }
        i = 0;
        for (ComplianceObligationEvidence e : evidence.findForVersions(tenantId, ids)) {
            evidence.save(ComplianceObligationEvidence.of(tenantId, next.getId(), new ObligationRules.Evidence(e.getDocumentType(), e.isMandatory()), i++));
        }
        return detail(tenantId, o);
    }

    @Transactional
    public ObligationDetail submit(TenantId tenantId, UUID obligationId, UUID versionId, UUID by) {
        ComplianceObligation o = find(tenantId, obligationId);
        ComplianceObligationVersion v = findVersion(tenantId, obligationId, versionId);
        transition(() -> v.submit(by));
        versions.save(v);
        return detail(tenantId, o);
    }

    @Transactional
    public ObligationDetail approve(TenantId tenantId, UUID obligationId, UUID versionId, ApproveRequest r, UUID by) {
        ComplianceObligation o = find(tenantId, obligationId);
        ComplianceObligationVersion v = findVersion(tenantId, obligationId, versionId);
        LocalDate from = r == null ? null : r.effectiveFrom();
        transition(() -> v.approve(by, from, r == null ? null : r.exceptionReason(), r == null ? null : r.note()));
        // the version before it stops the day before this one starts
        versions.findForObligation(tenantId, obligationId).stream()
                .filter(p -> p.isApproved() && p.getVersionNumber() < v.getVersionNumber())
                .max(Comparator.comparingInt(ComplianceObligationVersion::getVersionNumber))
                .ifPresent(prev -> {
                    if (prev.getEffectiveTo() == null || !prev.getEffectiveTo().isBefore(from)) {
                        transition(() -> prev.endOn(ObligationRules.endOfPrevious(prev.getEffectiveFrom(), from)));
                        versions.save(prev);
                    }
                });
        versions.save(v);
        return detail(tenantId, o);
    }

    @Transactional
    public ObligationDetail reject(TenantId tenantId, UUID obligationId, UUID versionId, String reason, UUID by) {
        ComplianceObligation o = find(tenantId, obligationId);
        ComplianceObligationVersion v = findVersion(tenantId, obligationId, versionId);
        transition(() -> v.reject(by, reason));
        versions.save(v);
        return detail(tenantId, o);
    }

    /** Ends the version now in force from a date. A draft or a later version is left alone. */
    @Transactional
    public ObligationDetail retire(TenantId tenantId, UUID obligationId, LocalDate on, UUID by) {
        ComplianceObligation o = find(tenantId, obligationId);
        LocalDate date = on == null ? LocalDate.now() : on;
        ComplianceObligationVersion open = versions.findForObligation(tenantId, obligationId).stream()
                .filter(v -> v.isApproved() && v.getEffectiveTo() == null)
                .max(Comparator.comparingInt(ComplianceObligationVersion::getVersionNumber))
                .orElseThrow(() -> new HandyFlowException("No approved version is open-ended", HttpStatus.CONFLICT, "OBLIGATION_NOT_IN_FORCE"));
        transition(() -> open.endOn(date));
        versions.save(open);
        return detail(tenantId, o);
    }

    // ---- helpers ----

    private void fill(TenantId tenantId, ComplianceObligationVersion v, SaveVersionRequest r, UUID by) {
        try {
            v.edit(new ComplianceObligationVersion.Content(r.name(), r.description(), r.legalSource(), r.regulator(), r.jurisdiction(),
                    r.frequency(), r.dueRuleType(), r.dueMonth(), r.dueDay(), r.dueOffsetDays(), r.dueOffsetMonths(), r.validityMonths(),
                    r.riskLevel(), r.responsibleRole(), r.guidance(), r.changeSummary()), by);
            checkChildren(r);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "OBLIGATION_VERSION_LOCKED");
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
    }

    private static List<ObligationRules.Condition> checked(SaveVersionRequest r) {
        List<ObligationRules.Condition> out = new ArrayList<>();
        if (r.conditions() != null) for (ConditionInput c : r.conditions()) out.add(ObligationRules.condition(c.field(), c.operator(), c.value()));
        ObligationRules.consistent(out);
        return out;
    }

    private static void checkChildren(SaveVersionRequest r) {
        checked(r);
        if (r.evidence() != null) for (EvidenceInput e : r.evidence()) ObligationRules.evidence(e.documentType(), e.mandatory());
    }

    private void saveChildren(TenantId tenantId, UUID versionId, SaveVersionRequest r) {
        conditions.deleteForVersion(versionId);
        evidence.deleteForVersion(versionId);
        int i = 0;
        for (ObligationRules.Condition c : checked(r)) conditions.save(ComplianceObligationCondition.of(tenantId, versionId, c, i++));
        i = 0;
        if (r.evidence() != null) {
            for (EvidenceInput e : r.evidence()) evidence.save(ComplianceObligationEvidence.of(tenantId, versionId, ObligationRules.evidence(e.documentType(), e.mandatory()), i++));
        }
    }

    private static void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "OBLIGATION_WORKFLOW");
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
    }

    private static SaveVersionRequest emptyRequest() {
        return new SaveVersionRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private ObligationDetail detail(TenantId tenantId, ComplianceObligation o) {
        List<ComplianceObligationVersion> vs = versions.findForObligation(tenantId, o.getId());
        List<UUID> ids = vs.stream().map(ComplianceObligationVersion::getId).toList();
        Map<UUID, List<ConditionView>> cs = ids.isEmpty() ? Map.of() : conditions.findForVersions(tenantId, ids).stream()
                .collect(Collectors.groupingBy(ComplianceObligationCondition::getVersionId, LinkedHashMap::new,
                        Collectors.mapping(c -> new ConditionView(c.getFieldName(), c.getOperator(), c.getValue()), Collectors.toList())));
        Map<UUID, List<EvidenceView>> es = ids.isEmpty() ? Map.of() : evidence.findForVersions(tenantId, ids).stream()
                .collect(Collectors.groupingBy(ComplianceObligationEvidence::getVersionId, LinkedHashMap::new,
                        Collectors.mapping(e -> new EvidenceView(e.getDocumentType(), e.isMandatory()), Collectors.toList())));
        Integer inForce = ObligationRules.effectiveOn(vs.stream().map(ComplianceObligationVersion::span).toList(), LocalDate.now());
        return new ObligationDetail(o.getId(), o.getCode(), vs.stream().map(v -> new VersionView(v.getId(), v.getVersionNumber(), v.getReviewStatus(),
                v.getName(), v.getDescription(), v.getLegalSource(), v.getRegulator(), v.getJurisdiction(), v.getFrequency(), v.getDueRuleType(),
                v.getDueMonth(), v.getDueDay(), v.getDueOffsetDays(), v.getDueOffsetMonths(), v.getValidityMonths(), v.getRiskLevel(),
                v.getResponsibleRole(), v.getGuidance(), v.getChangeSummary(), v.getAuthoredBy(), v.getSubmittedBy(), v.getSubmittedAt(),
                v.getReviewedBy(), v.getReviewedAt(), v.getReviewNote(), v.getReviewExceptionReason(), v.getEffectiveFrom(), v.getEffectiveTo(),
                inForce != null && inForce == v.getVersionNumber(), cs.getOrDefault(v.getId(), List.of()), es.getOrDefault(v.getId(), List.of()))).toList());
    }

    private ComplianceObligation find(TenantId tenantId, UUID id) {
        return obligations.findByIdForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceObligation", id.toString()));
    }

    private ComplianceObligationVersion findVersion(TenantId tenantId, UUID obligationId, UUID versionId) {
        return versions.findOne(tenantId, obligationId, versionId).orElseThrow(() -> new ResourceNotFoundException("ComplianceObligationVersion", versionId.toString()));
    }

    private static HandyFlowException bad(IllegalArgumentException e) {
        return new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "OBLIGATION_INVALID");
    }
}
