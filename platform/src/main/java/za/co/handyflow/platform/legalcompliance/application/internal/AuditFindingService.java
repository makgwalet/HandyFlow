package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.FindingNoteRequest;
import za.co.handyflow.platform.legalcompliance.dto.RemediationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Findings raised by audits. Closure needs: a closer who is not the owner or the responder (unconditional 403), and, except for observations,
 * a management response plus at least one corrective/preventive action with every non-cancelled action verified (AssuranceRules.closeBlocker).
 * Raising a finding does not change a control's effectiveness; only an approved control test does that.
 */
@Service
@RequiredArgsConstructor
public class AuditFindingService {

    static final String ENTITY = "AUDIT_FINDING";

    private final AuditFindingRepository findings;
    private final AuditRepository audits;
    private final ControlRepository controls;
    private final CapaRepository capas;
    private final RegulatoryAuditService trail;

    @Transactional
    public AuditFindingResponse raise(TenantId tenantId, UUID auditId, AuditFindingRequest req, UUID userId, String userName) {
        Audit audit = audits.findForTenant(tenantId, auditId).orElseThrow(() -> new ResourceNotFoundException("Audit", auditId.toString()));
        if (!audit.acceptsFindings()) throw new HandyFlowException("Findings can only be raised while the audit is in fieldwork or reporting — current: " + audit.getStatus(), HttpStatus.CONFLICT, "INVALID_STATE");
        Control control = req.controlId() == null ? null : controls.findForTenant(tenantId, req.controlId()).orElseThrow(() -> new ResourceNotFoundException("Control", req.controlId().toString()));
        UUID ownerId = req.ownerId();
        String ownerName = req.ownerName();
        if ((ownerName == null || ownerName.isBlank()) && control != null) { ownerId = control.getOwnerId(); ownerName = control.getOwnerName(); }
        LocalDate due = req.dueDate() != null ? req.dueDate() : req.severity() == null ? null : AssuranceRules.defaultDue(req.severity(), LocalDate.now());
        UUID fOwnerId = ownerId; String fOwnerName = ownerName;
        AuditFinding f = guarded(() -> AuditFinding.raise(tenantId, String.format("AFN-%05d", findings.nextRef()), auditId, req.controlId(), req.findingType(), req.title(), req.description(),
                req.rootCause(), req.severity(), fOwnerId, fOwnerName, due, userId, userName));
        findings.save(f);
        trail.record(tenantId.getValue(), ENTITY, f.getId(), "RAISED", f.getReference() + " (" + f.getFindingType() + ", " + f.getSeverity() + ") in " + audit.getReference() + ", due " + f.getDueDate(), userId, userName);
        return view(tenantId, f, audit.getReference());
    }

    @Transactional(readOnly = true)
    public List<AuditFindingResponse> list(TenantId tenantId, UUID auditId, AuditFindingStatus status, boolean includeClosed) {
        return findings.searchCapped(tenantId, auditId, status, includeClosed).stream().map(f -> view(tenantId, f, null)).toList();
    }

    @Transactional(readOnly = true)
    public AuditFindingResponse get(TenantId tenantId, UUID id) { return view(tenantId, load(tenantId, id), null); }

    @Transactional
    public AuditFindingResponse respond(TenantId tenantId, UUID id, ManagementResponseRequest req, UUID userId, String userName) {
        AuditFinding f = load(tenantId, id);
        guarded(() -> { f.respond(req.response(), req.ownerId(), req.ownerName(), req.dueDate(), userId, userName); return null; });
        findings.save(f);
        trail.record(tenantId.getValue(), ENTITY, id, "RESPONDED", "Owner " + req.ownerName() + ", due " + req.dueDate() + ": " + req.response().trim(), userId, userName);
        return view(tenantId, f, null);
    }

    @Transactional
    public AuditFindingResponse close(TenantId tenantId, UUID id, FindingNoteRequest req, UUID userId, String userName) {
        AuditFinding f = load(tenantId, id);
        if (userId != null && (userId.equals(f.getOwnerId()) || userId.equals(f.getRespondedBy())))
            throw new HandyFlowException("Segregation of duties: you own this finding or gave the management response, so you cannot close it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
        String blocker = AssuranceRules.closeBlocker(f.getFindingType(), capas.findByAuditFinding(tenantId, id).stream().map(Capa::getStatus).toList());
        if (blocker != null) throw new HandyFlowException(blocker, HttpStatus.CONFLICT, "CAPA_INCOMPLETE");
        guarded(() -> { f.close(userId, userName, req.note()); return null; });
        findings.save(f);
        trail.record(tenantId.getValue(), ENTITY, id, "CLOSED", req.note().trim(), userId, userName);
        return view(tenantId, f, null);
    }

    AuditFinding load(TenantId tenantId, UUID id) { return findings.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Audit finding", id.toString())); }

    private AuditFindingResponse view(TenantId tenantId, AuditFinding f, String auditRef) {
        String ref = auditRef != null ? auditRef : audits.findForTenant(tenantId, f.getAuditId()).map(Audit::getReference).orElse(null);
        String control = f.getControlId() == null ? null : controls.findForTenant(tenantId, f.getControlId()).map(c -> c.getReference() + " — " + c.getTitle()).orElse(null);
        List<CapaStatus> statuses = capas.findByAuditFinding(tenantId, f.getId()).stream().map(Capa::getStatus).toList();
        int live = (int) statuses.stream().filter(s -> s != CapaStatus.CANCELLED).count();
        int verified = (int) statuses.stream().filter(s -> s == CapaStatus.VERIFIED).count();
        String blocker = f.isOpen() ? AssuranceRules.closeBlocker(f.getFindingType(), statuses) : null;
        return AuditFindingResponse.of(f, ref, control, live, verified, blocker, LocalDate.now());
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
