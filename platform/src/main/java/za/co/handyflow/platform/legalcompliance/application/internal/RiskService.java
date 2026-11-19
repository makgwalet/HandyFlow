package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.GrcLinkRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.RiskAssessmentRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.RiskRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.RiskSettingsRepository;
import za.co.handyflow.platform.legalcompliance.dto.GrcDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise risk register.
 * <ul>
 *  <li>Assessments are append-only (supersede first, flush, then insert — the one-current unique index requires that order).</li>
 *  <li>A risk whose residual score is above tolerance cannot be formally accepted; an acceptance lapses when a reassessment puts the
 *      residual above tolerance, or when its expiry date passes.</li>
 *  <li>Residual is the assessor's judgement. It is not derived from, or verified against, control test results (a later chunk).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RiskService {

    private static final String RISK = "RISK";

    private final RiskRepository risks;
    private final RiskAssessmentRepository assessments;
    private final RiskSettingsRepository settingsRepo;
    private final GrcLinkRepository links;
    private final GrcLinkService linkService;
    private final RegulatoryAuditService audit;

    @Value("${app.legalcompliance.sod.enforce:false}")
    private boolean enforceSegregationOfDuties;

    // ── Settings ──

    @Transactional(readOnly = true)
    public SettingsResponse getSettings(TenantId tenantId) {
        RiskSettings s = settingsRepo.findForTenant(tenantId).orElseGet(() -> RiskSettings.defaults(tenantId));
        return new SettingsResponse(s.getAppetiteMax(), s.getToleranceMax());
    }

    /** Changing thresholds never rewrites past assessments: each stores the thresholds that applied when it was made. */
    @Transactional
    public SettingsResponse saveSettings(TenantId tenantId, SettingsRequest req, UUID userId, String userName) {
        RiskSettings s = settingsRepo.findForTenant(tenantId).orElseGet(() -> RiskSettings.defaults(tenantId));
        try { s.update(req.appetiteMax(), req.toleranceMax(), userId); } catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
        settingsRepo.save(s);
        audit.record(tenantId.getValue(), "RISK_SETTINGS", s.getId(), "UPDATED", "Appetite " + req.appetiteMax() + ", tolerance " + req.toleranceMax(), userId, userName);
        return new SettingsResponse(s.getAppetiteMax(), s.getToleranceMax());
    }

    // ── CRUD ──

    @Transactional
    public RiskResponse create(TenantId tenantId, RiskRequest req, UUID userId, String userName) {
        Risk r = guarded(() -> Risk.create(tenantId, String.format("RSK-%05d", risks.nextRef()), req.title(), req.description(), req.category(), req.emerging(),
                req.ownerId(), req.ownerName(), req.nextReviewDate(), userId));
        risks.save(r);
        audit.record(tenantId.getValue(), RISK, r.getId(), "CREATED", r.getReference() + " " + r.getTitle(), userId, userName);
        return view(tenantId, r);
    }

    @Transactional
    public RiskResponse update(TenantId tenantId, UUID id, RiskRequest req, UUID userId, String userName) {
        Risk r = load(tenantId, id);
        guarded(() -> { r.update(req.title(), req.description(), req.category(), req.emerging(), req.ownerId(), req.ownerName(), req.nextReviewDate()); return null; });
        risks.save(r);
        audit.record(tenantId.getValue(), RISK, id, "UPDATED", "Details updated", userId, userName);
        return view(tenantId, r);
    }

    @Transactional(readOnly = true)
    public List<RiskResponse> list(TenantId tenantId, RiskStatus status, RiskCategory category, boolean includeClosed) {
        Map<UUID, RiskAssessment> current = currentByRisk(tenantId);
        Map<UUID, Integer> controlCounts = countBySource(tenantId, GrcLinkType.RISK_CONTROL), obligationCounts = countBySource(tenantId, GrcLinkType.RISK_OBLIGATION);
        LocalDate today = LocalDate.now();
        return risks.search(tenantId, status, category, includeClosed).stream()
                .map(r -> RiskResponse.of(r, current.get(r.getId()), controlCounts.getOrDefault(r.getId(), 0), obligationCounts.getOrDefault(r.getId(), 0), today)).toList();
    }

    @Transactional(readOnly = true)
    public RiskDetailResponse detail(TenantId tenantId, UUID id) {
        Risk r = load(tenantId, id);
        return new RiskDetailResponse(view(tenantId, r), assessments.history(tenantId, id).stream().map(AssessmentResponse::of).toList(), linkService.linksFor(tenantId, RISK, id));
    }

    // ── Assessment ──

    @Transactional
    public RiskResponse assess(TenantId tenantId, UUID id, AssessRiskRequest req, UUID userId, String userName) {
        Risk r = load(tenantId, id);
        if (!r.isOpen()) throw conflict("INVALID_STATE", "The risk is closed — reopen it first");
        SettingsResponse s = getSettings(tenantId);
        RiskAssessment next = guarded(() -> RiskAssessment.create(tenantId, id, req.inherentLikelihood(), req.inherentImpact(), req.residualLikelihood(),
                req.residualImpact(), s.appetiteMax(), s.toleranceMax(), req.rationale(), userId, userName));

        assessments.findCurrent(tenantId, id).ifPresent(old -> { old.supersede(); assessments.saveAndFlush(old); });   // free the one-current slot first
        assessments.save(next);
        r.markAssessed();
        audit.record(tenantId.getValue(), RISK, id, "ASSESSED", "Inherent " + next.getInherentScore() + " (" + next.getInherentRating() + "), residual "
                + next.getResidualScore() + " (" + next.getResidualRating() + "), " + next.residualPosition(), userId, userName);

        if (r.getStatus() == RiskStatus.ACCEPTED && next.residualPosition() == RiskPosition.OUTSIDE_TOLERANCE) {
            r.lapseAcceptance();
            audit.record(tenantId.getValue(), RISK, id, "ACCEPTANCE_LAPSED", "Reassessed outside tolerance (residual " + next.getResidualScore() + ")", userId, userName);
        }
        risks.save(r);
        return view(tenantId, r);
    }

    // ── Treatment, acceptance, lifecycle ──

    @Transactional
    public RiskResponse startTreatment(TenantId tenantId, UUID id, TreatmentRequest req, UUID userId, String userName) {
        Risk r = load(tenantId, id);
        guarded(() -> { r.startTreatment(req.strategy(), req.plan(), req.dueDate()); return null; });
        risks.save(r);
        audit.record(tenantId.getValue(), RISK, id, "TREATMENT_PLANNED", req.strategy() + ", due " + req.dueDate() + ": " + req.plan().trim(), userId, userName);
        return view(tenantId, r);
    }

    @Transactional
    public RiskResponse accept(TenantId tenantId, UUID id, AcceptRiskRequest req, UUID userId, String userName) {
        Risk r = load(tenantId, id);
        RiskAssessment a = assessments.findCurrent(tenantId, id).orElseThrow(() -> conflict("NOT_ASSESSED", "Assess the risk before accepting it"));
        if (a.residualPosition() == RiskPosition.OUTSIDE_TOLERANCE)
            throw conflict("OUTSIDE_TOLERANCE", "Residual score " + a.getResidualScore() + " is above the tolerance of " + a.getToleranceMax() + " — it must be treated, not accepted");
        if (enforceSegregationOfDuties && userId != null && userId.equals(a.getAssessedBy()))
            throw new HandyFlowException("Segregation of duties: you cannot accept a risk you assessed", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
        guarded(() -> { r.accept(userId, userName, req.reason(), req.expiresOn(), LocalDate.now()); return null; });
        risks.save(r);
        audit.record(tenantId.getValue(), RISK, id, "ACCEPTED", "Until " + req.expiresOn() + " (residual " + a.getResidualScore() + "): " + req.reason().trim(), userId, userName);
        return view(tenantId, r);
    }

    @Transactional
    public RiskResponse close(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, String userName) {
        Risk r = load(tenantId, id);
        guarded(() -> { r.close(req.reason()); return null; });
        risks.save(r);
        audit.record(tenantId.getValue(), RISK, id, "CLOSED", req.reason().trim(), userId, userName);
        return view(tenantId, r);
    }

    @Transactional
    public RiskResponse reopen(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, String userName) {
        Risk r = load(tenantId, id);
        guarded(() -> { r.reopen(); return null; });
        risks.save(r);
        audit.record(tenantId.getValue(), RISK, id, "REOPENED", req.reason().trim(), userId, userName);
        return view(tenantId, r);
    }

    /** Called by the daily sweep. No user is involved, so the actor is recorded as the system. */
    @Transactional
    public void lapseExpiredAcceptance(TenantId tenantId, UUID id) {
        risks.findForTenant(tenantId, id).filter(r -> r.getStatus() == RiskStatus.ACCEPTED).ifPresent(r -> {
            String until = String.valueOf(r.getAcceptanceExpires());
            r.lapseAcceptance();
            risks.save(r);
            audit.record(tenantId.getValue(), RISK, id, "ACCEPTANCE_LAPSED", "Acceptance expired on " + until, null, "System");
        });
    }

    // ── Reporting ──

    @Transactional(readOnly = true)
    public HeatMapResponse heatMap(TenantId tenantId, boolean inherent) {
        Map<UUID, RiskAssessment> current = currentByRisk(tenantId);
        List<Risk> open = risks.search(tenantId, null, null, false);
        Map<String, List<String>> cells = new HashMap<>();
        int unassessed = 0;
        for (Risk r : open) {
            RiskAssessment a = current.get(r.getId());
            if (a == null) { unassessed++; continue; }
            int l = inherent ? a.getInherentLikelihood() : a.getResidualLikelihood(), i = inherent ? a.getInherentImpact() : a.getResidualImpact();
            cells.computeIfAbsent(l + ":" + i, k -> new ArrayList<>()).add(r.getReference());
        }
        List<HeatCell> out = new ArrayList<>();
        for (int l = 5; l >= 1; l--) for (int i = 1; i <= 5; i++) {
            List<String> refs = cells.getOrDefault(l + ":" + i, List.of());
            out.add(new HeatCell(l, i, refs.size(), refs));
        }
        return new HeatMapResponse(inherent ? "INHERENT" : "RESIDUAL", out, unassessed, open.size());
    }

    @Transactional(readOnly = true)
    public RiskSummary summary(TenantId tenantId) {
        Map<UUID, RiskAssessment> current = currentByRisk(tenantId);
        List<Risk> open = risks.search(tenantId, null, null, false);
        LocalDate today = LocalDate.now();
        long critical = 0, high = 0, above = 0, outside = 0, unassessed = 0;
        for (Risk r : open) {
            RiskAssessment a = current.get(r.getId());
            if (a == null) { unassessed++; continue; }
            if (a.getResidualRating() == RiskRating.CRITICAL) critical++;
            if (a.getResidualRating() == RiskRating.HIGH) high++;
            if (a.residualPosition() == RiskPosition.ABOVE_APPETITE) above++;
            if (a.residualPosition() == RiskPosition.OUTSIDE_TOLERANCE) outside++;
        }
        return new RiskSummary(open.size(), critical, high, above, outside, open.stream().filter(r -> r.getNextReviewDate().isBefore(today)).count(),
                open.stream().filter(r -> r.getStatus() == RiskStatus.ACCEPTED).count(), unassessed);
    }

    // ── internals ──

    private RiskResponse view(TenantId tenantId, Risk r) {
        return RiskResponse.of(r, assessments.findCurrent(tenantId, r.getId()).orElse(null),
                links.findBySource(tenantId, GrcLinkType.RISK_CONTROL, r.getId()).size(), links.findBySource(tenantId, GrcLinkType.RISK_OBLIGATION, r.getId()).size(), LocalDate.now());
    }

    private Map<UUID, RiskAssessment> currentByRisk(TenantId tenantId) {
        return assessments.findAllCurrent(tenantId).stream().collect(Collectors.toMap(RiskAssessment::getRiskId, a -> a, (x, y) -> x));
    }

    private Map<UUID, Integer> countBySource(TenantId tenantId, GrcLinkType type) {
        Map<UUID, Integer> m = new HashMap<>();
        links.findAllOfType(tenantId, type).forEach(l -> m.merge(l.getSourceId(), 1, Integer::sum));
        return m;
    }

    private Risk load(TenantId tenantId, UUID id) {
        return risks.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Risk", id.toString()));
    }

    /** Domain rules throw IllegalArgument (bad input) or IllegalState (wrong state); map them to 400 / 409. */
    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
        catch (IllegalStateException e) { throw conflict("INVALID_STATE", e.getMessage()); }
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "VALIDATION"); }
    private static HandyFlowException conflict(String code, String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }
}
