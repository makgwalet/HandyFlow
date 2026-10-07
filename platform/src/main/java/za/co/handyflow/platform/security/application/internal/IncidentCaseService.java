// security/application/internal/IncidentCaseService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.model.IncidentEvent;
import za.co.handyflow.platform.security.domain.model.IncidentWorkflow;
import za.co.handyflow.platform.security.domain.model.IncidentWorkflow.Action;
import za.co.handyflow.platform.security.domain.repository.IncidentEventRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.EvidenceItem;
import za.co.handyflow.platform.security.dto.IncidentCaseDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Working an incident: assign, escalate, note, reopen, evidence files, and the case view with its timeline.
 * Acknowledge and resolve stay in IncidentService (they also write timeline events). Which actions are
 * available is decided by IncidentWorkflow, and every action is recorded on the timeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentCaseService {

    static final String SOURCE_MODULE = "security";
    static final String ENTITY_TYPE = "Incident";

    private final IncidentRepository incidentRepo;
    private final IncidentEventRepository eventRepo;
    private final IncidentService incidentService;
    private final EvidenceFacade evidenceFacade;

    @Transactional(readOnly = true)
    public CaseDetail get(TenantId tenantId, UUID id) { return detail(find(tenantId, id)); }

    @Transactional
    public CaseDetail assign(TenantId tenantId, UUID id, AssignRequest req, UUID by, String byName) {
        Incident i = find(tenantId, id);
        require(i, Action.ASSIGN);
        if (req.assigneeName() == null || req.assigneeName().isBlank()) throw bad("Say who is dealing with this", "ASSIGNEE_REQUIRED");
        i.assign(req.assigneeName());
        incidentRepo.saveAndFlush(i);
        event(i, "ASSIGNED", null, "Assigned to " + i.getAssigneeName(), by, byName);
        return detail(i);
    }

    @Transactional
    public CaseDetail escalate(TenantId tenantId, UUID id, EscalateRequest req, UUID by, String byName) {
        Incident i = find(tenantId, id);
        require(i, Action.ESCALATE);
        if (req.reason() == null || req.reason().isBlank()) throw bad("Give a reason for escalating", "REASON_REQUIRED");
        String problem = IncidentWorkflow.escalationError(i.getSeverity(), req.severity());
        if (problem != null) throw bad(problem, "INVALID_ESCALATION");
        String from = i.getSeverity();
        String to = req.severity() == null || req.severity().isBlank() ? IncidentWorkflow.nextSeverity(from) : req.severity().toUpperCase();
        i.escalateTo(to);
        incidentRepo.saveAndFlush(i);
        event(i, "ESCALATED", null, "Severity " + from.toLowerCase() + " to " + to.toLowerCase() + ": " + req.reason().trim(), by, byName);
        log.info("[Security] Incident escalated id={} {} -> {}", id, from, to);
        return detail(i);
    }

    @Transactional
    public CaseDetail note(TenantId tenantId, UUID id, NoteRequest req, UUID by, String byName) {
        Incident i = find(tenantId, id);
        require(i, Action.NOTE);
        if (req.note() == null || req.note().isBlank()) throw bad("Write the note", "NOTE_REQUIRED");
        event(i, "NOTE", null, req.note(), by, byName);
        return detail(i);
    }

    @Transactional
    public CaseDetail reopen(TenantId tenantId, UUID id, ReopenRequest req, UUID by, String byName) {
        Incident i = find(tenantId, id);
        require(i, Action.REOPEN);
        if (req.reason() == null || req.reason().isBlank()) throw bad("Give a reason for reopening", "REASON_REQUIRED");
        i.reopen();
        incidentRepo.saveAndFlush(i);
        event(i, "REOPENED", "ACKNOWLEDGED", req.reason(), by, byName);
        return detail(i);
    }

    // ── Evidence ──────────────────────────────────────────────────────────────

    @Transactional
    public EvidenceResponse attach(TenantId tenantId, UUID id, MultipartFile file, String label, UUID by, String byName) {
        Incident i = find(tenantId, id);
        require(i, Action.EVIDENCE);
        if (file == null || file.isEmpty()) throw bad("Choose a file to attach", "MISSING_FILE");
        String type = label == null || label.isBlank() ? "Incident evidence" : label.trim();
        EvidenceResponse saved = evidenceFacade.attach(tenantId, file, type, SOURCE_MODULE, ENTITY_TYPE, id, null, by, byName);
        event(i, "EVIDENCE_ADDED", null, saved.fileName(), by, byName);
        return saved;
    }

    @Transactional(readOnly = true)
    public EvidenceFacade.DownloadedEvidence download(TenantId tenantId, UUID id, UUID evidenceId) {
        requireOwned(tenantId, id, evidenceId);
        return evidenceFacade.download(tenantId, evidenceId);
    }

    @Transactional
    public void removeFile(TenantId tenantId, UUID id, UUID evidenceId, UUID by, String byName) {
        Incident i = find(tenantId, id);
        require(i, Action.EVIDENCE);
        requireOwned(tenantId, id, evidenceId);
        evidenceFacade.detach(tenantId, evidenceId);
        event(i, "EVIDENCE_REMOVED", null, null, by, byName);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Incident find(TenantId tenantId, UUID id) {
        return incidentRepo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new ResourceNotFoundException("Incident", id.toString()));
    }

    private void requireOwned(TenantId tenantId, UUID id, UUID evidenceId) {
        find(tenantId, id);
        boolean owned = evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, id).stream().anyMatch(e -> e.id().equals(evidenceId));
        if (!owned) throw new ResourceNotFoundException("Evidence", evidenceId.toString());
    }

    private void require(Incident i, Action action) {
        if (!IncidentWorkflow.allowed(i.getStatus(), i.getSeverity()).contains(action))
            throw new HandyFlowException("That is not available while the incident is " + i.getStatus().toLowerCase(), HttpStatus.CONFLICT, "INCIDENT_STATE");
    }

    private void event(Incident i, String type, String toStatus, String note, UUID by, String byName) {
        eventRepo.save(IncidentEvent.of(i.getTenantId(), i.getId(), type, toStatus, note, by, byName));
    }

    private static HandyFlowException bad(String message, String code) { return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code); }

    CaseDetail detail(Incident i) {
        var events = eventRepo.findForIncident(i.getTenantId(), i.getId()).stream()
                .map(e -> new EventItem(e.getId(), e.getEventType(), e.getToStatus(), e.getNote(), e.getByName(), e.getAt())).toList();
        List<EvidenceItem> files = evidenceFacade.listFor(i.getTenantId(), SOURCE_MODULE, ENTITY_TYPE, i.getId()).stream()
                .map(e -> new EvidenceItem(e.id(), e.fileName(), e.evidenceType(), e.fileSizeBytes(), e.uploadedByName(), e.createdAt())).toList();
        return new CaseDetail(incidentService.getIncidentDetail(i.getTenantId(), i.getId()), i.getAssigneeName(), i.getAssignedAt(),
                IncidentWorkflow.allowed(i.getStatus(), i.getSeverity()).stream().map(Enum::name).toList(), events, files);
    }
}
