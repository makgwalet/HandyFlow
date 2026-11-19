package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.RemediationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corrective and preventive actions. Segregation of duties is unconditional here (not behind app.legalcompliance.sod.enforce):
 *  - the approver is neither the proposer nor the action owner;
 *  - the verifier is neither the action owner nor the person who completed it.
 * Evidence is held by the shared evidence module under sourceModule "legalcompliance", entity type "CAPA"; the files present at completion are
 * snapshotted, and verification is refused (409 EVIDENCE_REMOVED) if any have since been detached.
 */
@Service
@RequiredArgsConstructor
public class CapaService {

    static final String ENTITY = "CAPA";
    static final String SOURCE_MODULE = "legalcompliance";

    private final CapaRepository capas;
    private final FindingRepository findings;
    private final AuditFindingRepository auditFindings;
    private final IncidentRepository incidents;
    private final EvidenceFacade evidence;
    private final RegulatoryAuditService trail;

    @Transactional
    public CapaResponse propose(TenantId tenantId, CapaRequest req, UUID userId, String userName) {
        int parents = (req.findingId() != null ? 1 : 0) + (req.auditFindingId() != null ? 1 : 0) + (req.incidentId() != null ? 1 : 0);
        if (parents != 1) throw new HandyFlowException("Give exactly one of a finding, an audit finding or an incident", HttpStatus.BAD_REQUEST, "VALIDATION");
        if (req.incidentId() != null) {
            Incident i = incidents.findForTenant(tenantId, req.incidentId()).orElseThrow(() -> new ResourceNotFoundException("Incident", req.incidentId().toString()));
            if (!i.isOpen()) throw new HandyFlowException("The incident is already closed", HttpStatus.CONFLICT, "FINDING_CLOSED");
        } else if (req.findingId() != null) {
            Finding f = findings.findForTenant(tenantId, req.findingId()).orElseThrow(() -> new ResourceNotFoundException("Finding", req.findingId().toString()));
            if (!f.isOpen()) throw new HandyFlowException("The finding is already closed", HttpStatus.CONFLICT, "FINDING_CLOSED");
        } else {
            AuditFinding f = auditFindings.findForTenant(tenantId, req.auditFindingId()).orElseThrow(() -> new ResourceNotFoundException("Audit finding", req.auditFindingId().toString()));
            if (!f.isOpen()) throw new HandyFlowException("The audit finding is already closed", HttpStatus.CONFLICT, "FINDING_CLOSED");
        }
        Capa c = guarded(() -> Capa.propose(tenantId, String.format("CAPA-%05d", capas.nextRef()), req.findingId(), req.auditFindingId(), req.incidentId(), req.capaType(), req.title(), req.actionPlan(),
                req.rootCause(), req.ownerId(), req.ownerName(), req.dueDate(), userId, userName));
        capas.save(c);
        trail.record(tenantId.getValue(), ENTITY, c.getId(), "PROPOSED", c.getReference() + " " + c.getCapaType() + ", owner " + c.getOwnerName() + ", due " + c.getDueDate(), userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public CapaResponse revise(TenantId tenantId, UUID id, CapaReviseRequest req, UUID userId, String userName) {
        Capa c = load(tenantId, id);
        if (userId == null || !userId.equals(c.getCreatedBy())) throw new HandyFlowException("Only the person who proposed the action can revise it", HttpStatus.FORBIDDEN, "NOT_PROPOSER");
        guarded(() -> { c.revise(req.capaType(), req.title(), req.actionPlan(), req.rootCause(), req.ownerId(), req.ownerName(), req.dueDate()); return null; });
        capas.save(c);
        trail.record(tenantId.getValue(), ENTITY, id, "REVISED", "Revised after review", userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public CapaResponse approve(TenantId tenantId, UUID id, NoteRequest req, UUID userId, String userName) {
        Capa c = load(tenantId, id);
        requireNotProposerOrOwner(c, userId, "approve");
        guarded(() -> { c.approve(userId, userName, req == null ? null : req.note()); return null; });
        capas.save(c);
        trail.record(tenantId.getValue(), ENTITY, id, "APPROVED", req == null || req.note() == null ? "Approved" : req.note().trim(), userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public CapaResponse returnForRework(TenantId tenantId, UUID id, FindingNoteRequest req, UUID userId, String userName) {
        Capa c = load(tenantId, id);
        requireNotProposerOrOwner(c, userId, "review");
        guarded(() -> { c.returnForRework(userId, userName, req.note()); return null; });
        capas.save(c);
        trail.record(tenantId.getValue(), ENTITY, id, "RETURNED", req.note().trim(), userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public CapaResponse start(TenantId tenantId, UUID id, UUID userId, String userName) {
        Capa c = load(tenantId, id);
        guarded(() -> { c.start(); return null; });
        capas.save(c);
        trail.record(tenantId.getValue(), ENTITY, id, "STARTED", "Work started", userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public CapaResponse complete(TenantId tenantId, UUID id, CompleteCapaRequest req, UUID userId, String userName) {
        Capa c = load(tenantId, id);
        List<Map<String, Object>> snapshot = liveEvidence(tenantId, id).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id().toString());
            m.put("fileName", e.fileName());
            m.put("fileSizeBytes", e.fileSizeBytes());
            m.put("uploadedByName", e.uploadedByName());
            return m;
        }).toList();
        guarded(() -> { c.complete(userId, userName, req.note(), snapshot); return null; });
        capas.save(c);
        trail.record(tenantId.getValue(), ENTITY, id, "COMPLETED", snapshot.size() + " evidence file(s): " + req.note().trim(), userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public CapaResponse verify(TenantId tenantId, UUID id, VerifyCapaRequest req, UUID userId, String userName) {
        Capa c = load(tenantId, id);
        if (userId != null && (userId.equals(c.getOwnerId()) || userId.equals(c.getCompletedBy())))
            throw new HandyFlowException("Segregation of duties: you own or completed this action, so you cannot verify it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
        if (req.effective() && c.getStatus() == CapaStatus.COMPLETED && liveEvidence(tenantId, id).size() < c.getEvidenceSnapshot().size())
            throw new HandyFlowException("Evidence that was attached at completion has since been detached. Send the action back so it can be re-attached.", HttpStatus.CONFLICT, "EVIDENCE_REMOVED");
        guarded(() -> { c.verify(req.effective(), userId, userName, req.note()); return null; });
        capas.save(c);
        trail.record(tenantId.getValue(), ENTITY, id, req.effective() ? "VERIFIED" : "NOT_EFFECTIVE", req.note().trim(), userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public CapaResponse cancel(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, String userName) {
        Capa c = load(tenantId, id);
        guarded(() -> { c.cancel(req.reason()); return null; });
        capas.save(c);
        trail.record(tenantId.getValue(), ENTITY, id, "CANCELLED", req.reason().trim(), userId, userName);
        return view(tenantId, c);
    }

    @Transactional(readOnly = true)
    public List<CapaResponse> list(TenantId tenantId, CapaStatus status, boolean includeFinished, UUID findingId, UUID auditFindingId) {
        return capas.search(tenantId, status, includeFinished).stream()
                .filter(c -> findingId == null || findingId.equals(c.getFindingId()))
                .filter(c -> auditFindingId == null || auditFindingId.equals(c.getAuditFindingId()))
                .map(c -> view(tenantId, c)).toList();
    }

    @Transactional(readOnly = true)
    public CapaDetailResponse detail(TenantId tenantId, UUID id) {
        Capa c = load(tenantId, id);
        List<EvidenceRef> live = liveEvidence(tenantId, id).stream().map(e -> new EvidenceRef(e.id(), e.fileName(), e.fileSizeBytes(), e.uploadedByName())).toList();
        return new CapaDetailResponse(view(tenantId, c), live);
    }

    @Transactional(readOnly = true)
    public CapaSummary summary(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        List<Capa> open = capas.search(tenantId, null, false);
        return new CapaSummary(count(open, CapaStatus.PROPOSED), count(open, CapaStatus.APPROVED), count(open, CapaStatus.IN_PROGRESS), count(open, CapaStatus.COMPLETED),
                open.stream().filter(c -> c.isOverdue(today)).count());
    }

    // ── internals ──

    private Capa load(TenantId tenantId, UUID id) { return capas.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Corrective action", id.toString())); }

    private List<EvidenceResponse> liveEvidence(TenantId tenantId, UUID id) { return evidence.listFor(tenantId, SOURCE_MODULE, ENTITY, id); }

    private static void requireNotProposerOrOwner(Capa c, UUID userId, String verb) {
        if (userId != null && (userId.equals(c.getCreatedBy()) || userId.equals(c.getOwnerId())))
            throw new HandyFlowException("Segregation of duties: you proposed or own this action, so you cannot " + verb + " it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
    }

    private CapaResponse view(TenantId tenantId, Capa c) {
        String label;
        if (c.getIncidentId() != null) label = incidents.findForTenant(tenantId, c.getIncidentId()).map(i -> i.getReference() + " — " + i.getTitle()).orElse(null);
        else if (c.getAuditFindingId() != null) label = auditFindings.findForTenant(tenantId, c.getAuditFindingId()).map(f -> f.getReference() + " — " + f.getTitle()).orElse(null);
        else label = findings.findForTenant(tenantId, c.getFindingId()).map(f -> f.getReference() + " — " + f.getTitle()).orElse(null);
        return CapaResponse.of(c, label, LocalDate.now());
    }

    private static long count(List<Capa> all, CapaStatus s) { return all.stream().filter(c -> c.getStatus() == s).count(); }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
