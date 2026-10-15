package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.internalaudit.application.internal.ControlLibrary.Control;
import za.co.handyflow.platform.internalaudit.application.internal.ProgrammeSummary.Counts;
import za.co.handyflow.platform.internalaudit.domain.model.AuditProgrammeStep;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditEngagementRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditProgrammeStepRepository;
import za.co.handyflow.platform.internalaudit.dto.AuditProgrammeDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.function.Supplier;

/** Puts library checks onto an engagement and records what the auditor found. Every step goes on the audit trail. */
@Service
@RequiredArgsConstructor
public class AuditProgrammeService {
    public record Actor(UUID userId, String name) {}
    static final String NOTICE = "References are a starting point. Confirm them against the current law or standard and the company's own circumstances; some Acts apply only to certain kinds of business. This is not legal advice.";

    private final AuditProgrammeStepRepository steps;
    private final AuditEngagementRepository engagements;
    private final AuditFindingRepository findings;
    private final AuditTrailFacade trail;

    public Library library() {
        return new Library(ControlLibrary.DOMAIN_NAMES, ControlLibrary.ALL.stream().map(c -> new ControlView(c.code(), c.domain(), c.area(), c.objective(), c.procedure(), c.reference())).toList(), NOTICE);
    }

    @Transactional(readOnly = true)
    public Programme get(TenantId tenant, UUID engagementId) {
        UUID t = tenant.getValue();
        engagement(t, engagementId);
        return programme(t, engagementId);
    }

    /** Adds the chosen domains and controls. Controls already on the engagement are left exactly as they are. */
    @Transactional
    public Programme apply(TenantId tenant, Actor actor, UUID engagementId, List<String> domains, List<String> codes) {
        UUID t = tenant.getValue();
        requireOpen(engagement(t, engagementId));
        if ((domains == null || domains.isEmpty()) && (codes == null || codes.isEmpty())) throw bad("Choose at least one domain or control", "PRG_INVALID");
        List<Control> chosen = guard(() -> ControlLibrary.select(domains, codes));
        Set<String> have = new HashSet<>();
        steps.findByTenantIdAndEngagementIdOrderByControlCodeAsc(t, engagementId).forEach(s -> have.add(s.getControlCode()));
        List<String> added = new ArrayList<>();
        for (Control c : chosen) if (have.add(c.code())) { steps.save(AuditProgrammeStep.from(t, engagementId, c, actor.userId())); added.add(c.code()); }
        if (!added.isEmpty()) trail.record(tenant, "AUDIT_PROGRAMME_APPLIED", "AUDIT_ENGAGEMENT", engagementId, null, Map.of("controls", added));
        return programme(t, engagementId);
    }

    @Transactional
    public Programme record(TenantId tenant, Actor actor, UUID engagementId, String code, String status, String conclusion, String note) {
        UUID t = tenant.getValue();
        requireOpen(engagement(t, engagementId));
        var step = step(t, engagementId, code);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", step.getStatus()); before.put("conclusion", step.getConclusion());
        guard(() -> step.record(status, conclusion, note, actor.userId(), actor.name()));
        steps.save(step);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", step.getStatus()); after.put("conclusion", step.getConclusion()); after.put("control", step.getControlCode());
        trail.record(tenant, "AUDIT_PROGRAMME_RECORDED", "AUDIT_ENGAGEMENT", engagementId, before, after);
        return programme(t, engagementId);
    }

    /** Ties a result to the finding raised for it. The finding must belong to the same engagement. */
    @Transactional
    public Programme linkFinding(TenantId tenant, Actor actor, UUID engagementId, String code, UUID findingId) {
        UUID t = tenant.getValue();
        requireOpen(engagement(t, engagementId));
        var step = step(t, engagementId, code);
        if (findingId != null && findings.findByEngagement(t, engagementId).stream().noneMatch(f -> f.getId().equals(findingId)))
            throw bad("That finding is not on this engagement", "PRG_INVALID");
        step.linkFinding(findingId);
        steps.save(step);
        trail.record(tenant, "AUDIT_PROGRAMME_LINKED", "AUDIT_ENGAGEMENT", engagementId, null, Map.of("control", step.getControlCode(), "finding", String.valueOf(findingId)));
        return programme(t, engagementId);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────

    private za.co.handyflow.platform.internalaudit.domain.model.AuditEngagement engagement(UUID t, UUID id) {
        return engagements.findByTenantAndId(t, id).orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", String.valueOf(id)));
    }
    private static void requireOpen(za.co.handyflow.platform.internalaudit.domain.model.AuditEngagement e) {
        if ("CLOSED".equals(e.getStatus())) throw new HandyFlowException("This engagement is closed", HttpStatus.CONFLICT, "PRG_CLOSED");
    }
    private AuditProgrammeStep step(UUID t, UUID engagementId, String code) {
        return steps.findByTenantIdAndEngagementIdAndControlCode(t, engagementId, code == null ? "" : code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("AuditProgrammeStep", String.valueOf(code)));
    }

    private Programme programme(UUID t, UUID engagementId) {
        var list = steps.findByTenantIdAndEngagementIdOrderByControlCodeAsc(t, engagementId);
        var r = ProgrammeSummary.build(list.stream().map(s -> new ProgrammeSummary.StepIn(s.getDomain(), s.getStatus(), s.getConclusion(), s.needsFinding())).toList());
        Map<String, CountsView> by = new LinkedHashMap<>();
        r.byDomain().forEach((d, c) -> by.put(d, view(c)));
        return new Programme(engagementId, view(r.overall()), by, list.stream().map(s -> new StepView(s.getControlCode(), s.getDomain(), s.getArea(), s.getObjective(), s.getProcedure(), s.getReference(),
                s.getStatus(), s.getConclusion(), s.getNote(), s.getFindingId(), s.needsFinding(), s.getUpdatedByName(), s.getUpdatedAt())).toList());
    }
    private static CountsView view(Counts c) { return new CountsView(c.total(), c.completed(), c.notApplicable(), c.inProgress(), c.notStarted(), c.ineffective(), c.partial(), c.needingFinding(), c.donePct()); }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
    private static void guard(Runnable r) { try { r.run(); } catch (IllegalArgumentException e) { throw bad(e.getMessage(), "PRG_INVALID"); } }
    private static <T> T guard(Supplier<T> s) { try { return s.get(); } catch (IllegalArgumentException e) { throw bad(e.getMessage(), "PRG_INVALID"); } }
}
