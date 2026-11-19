package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.RemediationDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Audit engagements. An internal auditor may not audit a control they own: checked when the audit is planned or edited and again when
 * fieldwork starts (ownership can change in between). External audits carry the firm's name only, so no ownership check applies.
 * A started audit that has findings cannot be cancelled — the findings need their audit.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    static final String ENTITY = "AUDIT";

    private final AuditRepository audits;
    private final AuditFindingRepository findings;
    private final ControlRepository controls;
    private final RegulatoryAuditService trail;

    @Transactional
    public AuditResponse plan(TenantId tenantId, AuditRequest req, UUID userId, String userName) {
        List<UUID> scope = req.controlIds() == null ? List.of() : req.controlIds();
        validateScope(tenantId, scope);
        Audit a = guarded(() -> Audit.plan(tenantId, String.format("AUD-%05d", audits.nextRef()), req.title(), req.auditType(), req.scope(), req.objectives(), req.workProgramme(),
                req.auditorId(), req.leadAuditorName(), req.auditingFirm(), req.plannedStart(), req.plannedEnd(), scope, userId, userName));
        requireIndependent(tenantId, a);
        audits.save(a);
        trail.record(tenantId.getValue(), ENTITY, a.getId(), "PLANNED", a.getReference() + " " + a.getAuditType() + " audit led by " + a.getLeadAuditorName(), userId, userName);
        return view(tenantId, a);
    }

    @Transactional
    public AuditResponse update(TenantId tenantId, UUID id, AuditRequest req, UUID userId, String userName) {
        Audit a = load(tenantId, id);
        List<UUID> scope = req.controlIds() == null ? List.of() : req.controlIds();
        validateScope(tenantId, scope);
        guarded(() -> { a.update(req.title(), req.auditType(), req.scope(), req.objectives(), req.workProgramme(), req.auditorId(), req.leadAuditorName(), req.auditingFirm(),
                req.plannedStart(), req.plannedEnd(), scope); return null; });
        requireIndependent(tenantId, a);
        audits.save(a);
        trail.record(tenantId.getValue(), ENTITY, id, "UPDATED", "Scope, plan or team changed", userId, userName);
        return view(tenantId, a);
    }

    @Transactional
    public AuditResponse startFieldwork(TenantId tenantId, UUID id, UUID userId, String userName) {
        Audit a = load(tenantId, id);
        requireIndependent(tenantId, a);
        guarded(() -> { a.startFieldwork(); return null; });
        audits.save(a);
        trail.record(tenantId.getValue(), ENTITY, id, "FIELDWORK_STARTED", "Fieldwork started", userId, userName);
        return view(tenantId, a);
    }

    @Transactional
    public AuditResponse moveToReporting(TenantId tenantId, UUID id, UUID userId, String userName) {
        Audit a = load(tenantId, id);
        guarded(() -> { a.moveToReporting(); return null; });
        audits.save(a);
        trail.record(tenantId.getValue(), ENTITY, id, "REPORTING", "Fieldwork finished; reporting", userId, userName);
        return view(tenantId, a);
    }

    @Transactional
    public AuditResponse complete(TenantId tenantId, UUID id, CompleteAuditRequest req, UUID userId, String userName) {
        Audit a = load(tenantId, id);
        guarded(() -> { a.complete(req.rating(), req.conclusion(), userName); return null; });
        audits.save(a);
        trail.record(tenantId.getValue(), ENTITY, id, "COMPLETED", req.rating() + ": " + req.conclusion().trim(), userId, userName);
        return view(tenantId, a);
    }

    @Transactional
    public AuditResponse cancel(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, String userName) {
        Audit a = load(tenantId, id);
        if (!findings.findByAudit(tenantId, id).isEmpty())
            throw new HandyFlowException("This audit has findings and cannot be cancelled; complete it instead", HttpStatus.CONFLICT, "AUDIT_HAS_FINDINGS");
        guarded(() -> { a.cancel(req.reason()); return null; });
        audits.save(a);
        trail.record(tenantId.getValue(), ENTITY, id, "CANCELLED", req.reason().trim(), userId, userName);
        return view(tenantId, a);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> list(TenantId tenantId, AuditStatus status) { return audits.search(tenantId, status).stream().map(a -> view(tenantId, a)).toList(); }

    @Transactional(readOnly = true)
    public AuditResponse get(TenantId tenantId, UUID id) { return view(tenantId, load(tenantId, id)); }

    @Transactional(readOnly = true)
    public AuditSummary summary(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        List<Audit> all = audits.search(tenantId, null);
        List<AuditFinding> openFindings = findings.search(tenantId, null, null, false);
        return new AuditSummary(count(all, AuditStatus.PLANNED), count(all, AuditStatus.FIELDWORK), count(all, AuditStatus.REPORTING), all.stream().filter(a -> a.isOverdue(today)).count(),
                all.stream().filter(a -> a.getStatus() == AuditStatus.COMPLETED && a.getCompletedAt() != null && a.getCompletedAt().atZone(java.time.ZoneId.of("Africa/Johannesburg")).getYear() == today.getYear()).count(),
                openFindings.size(), openFindings.stream().filter(f -> f.isOverdue(today)).count());
    }

    Audit load(TenantId tenantId, UUID id) { return audits.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Audit", id.toString())); }

    // ── internals ──

    private void validateScope(TenantId tenantId, List<UUID> scope) {
        for (UUID cid : scope) controls.findForTenant(tenantId, cid).orElseThrow(() -> new ResourceNotFoundException("Control", cid.toString()));
    }

    private void requireIndependent(TenantId tenantId, Audit a) {
        if (a.getAuditType() != AuditType.INTERNAL || a.getAuditorId() == null) return;
        List<UUID> owners = a.getControlIds().stream().map(cid -> controls.findForTenant(tenantId, cid).map(Control::getOwnerId).orElse(null)).filter(java.util.Objects::nonNull).toList();
        if (AssuranceRules.auditorOwnsScopeControl(a.getAuditorId(), owners))
            throw new HandyFlowException("Auditor independence: the lead auditor owns a control that is in the scope of this audit", HttpStatus.CONFLICT, "AUDITOR_NOT_INDEPENDENT");
    }

    private AuditResponse view(TenantId tenantId, Audit a) {
        List<AuditFinding> fs = findings.findByAudit(tenantId, a.getId());
        List<String> labels = a.getControlIds().stream().map(cid -> controls.findForTenant(tenantId, cid).map(c -> c.getReference() + " — " + c.getTitle()).orElse(cid.toString())).toList();
        return AuditResponse.of(a, labels, fs.size(), (int) fs.stream().filter(AuditFinding::isOpen).count(), LocalDate.now());
    }

    private static long count(List<Audit> all, AuditStatus s) { return all.stream().filter(a -> a.getStatus() == s).count(); }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
