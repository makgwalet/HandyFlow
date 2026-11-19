package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.FindingNoteRequest;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.GovernanceDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Incidents and breaches. Anyone signed in can report one (the report endpoint has no module permission on purpose: people must be able to raise
 * a concern). Everything after that needs INCIDENT_MANAGE.
 *
 * The service does not decide whether the Regulator or data subjects must be notified, and it imposes no statutory deadline: it records the
 * assessor's decision and refuses to close an incident that involves personal information until that decision, and any notification it requires,
 * has been recorded. Closing also needs every corrective action on the incident verified or cancelled, and a closer who did not investigate it.
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    static final String ENTITY = "INCIDENT";

    private final IncidentRepository incidents;
    private final ControlRepository controls;
    private final CapaRepository capas;
    private final RegulatoryAuditService trail;

    @Transactional
    public IncidentResponse report(TenantId tenantId, IncidentRequest req, UUID userId, String userName) {
        validateControl(tenantId, req.controlId());
        Incident i = guarded(() -> Incident.report(tenantId, String.format("INC-%05d", incidents.nextRef()), req.incidentType(), req.title(), req.description(), req.severity(),
                req.occurredAt(), req.discoveredAt(), req.personalInfoInvolved(), req.dataSubjectsAffected(), req.dataCategories(), req.controlId(), userId, userName));
        incidents.save(i);
        trail.record(tenantId.getValue(), ENTITY, i.getId(), "REPORTED", i.getReference() + " " + i.getIncidentType() + " (" + i.getSeverity() + ")" + (i.isPersonalInfoInvolved() ? ", personal information involved" : ""), userId, userName);
        return view(tenantId, i);
    }

    @Transactional
    public IncidentResponse update(TenantId tenantId, UUID id, IncidentRequest req, UUID userId, String userName) {
        Incident i = load(tenantId, id);
        validateControl(tenantId, req.controlId());
        guarded(() -> { i.update(req.incidentType(), req.title(), req.description(), req.severity(), req.occurredAt(), req.discoveredAt(), req.personalInfoInvolved(),
                req.dataSubjectsAffected(), req.dataCategories(), req.controlId()); return null; });
        incidents.save(i);
        trail.record(tenantId.getValue(), ENTITY, id, "UPDATED", "Details changed", userId, userName);
        return view(tenantId, i);
    }

    @Transactional
    public IncidentResponse investigate(TenantId tenantId, UUID id, InvestigateRequest req, UUID userId, String userName) {
        Incident i = load(tenantId, id);
        guarded(() -> { i.startInvestigation(req.investigatorId(), req.investigatorName()); return null; });
        incidents.save(i);
        trail.record(tenantId.getValue(), ENTITY, id, "INVESTIGATING", "Investigator " + req.investigatorName().trim(), userId, userName);
        return view(tenantId, i);
    }

    @Transactional
    public IncidentResponse resolve(TenantId tenantId, UUID id, ResolveIncidentRequest req, UUID userId, String userName) {
        Incident i = load(tenantId, id);
        guarded(() -> { i.resolve(req.containmentActions(), req.rootCause(), req.resolutionNote()); return null; });
        incidents.save(i);
        trail.record(tenantId.getValue(), ENTITY, id, "RESOLVED", req.resolutionNote().trim(), userId, userName);
        return view(tenantId, i);
    }

    @Transactional
    public IncidentResponse assess(TenantId tenantId, UUID id, AssessRequest req, UUID userId, String userName) {
        Incident i = load(tenantId, id);
        guarded(() -> { i.assessNotification(req.required(), req.rationale(), userName); return null; });
        incidents.save(i);
        trail.record(tenantId.getValue(), ENTITY, id, "NOTIFICATION_ASSESSED", (req.required() ? "Notification required: " : "Notification not required: ") + req.rationale().trim(), userId, userName);
        return view(tenantId, i);
    }

    @Transactional
    public IncidentResponse regulatorNotified(TenantId tenantId, UUID id, RegulatorNotifiedRequest req, UUID userId, String userName) {
        Incident i = load(tenantId, id);
        guarded(() -> { i.recordRegulatorNotified(req.notifiedAt(), req.reference()); return null; });
        incidents.save(i);
        trail.record(tenantId.getValue(), ENTITY, id, "REGULATOR_NOTIFIED", "On " + req.notifiedAt() + (req.reference() == null || req.reference().isBlank() ? "" : ", reference " + req.reference().trim()), userId, userName);
        return view(tenantId, i);
    }

    @Transactional
    public IncidentResponse subjectsNotified(TenantId tenantId, UUID id, SubjectsNotifiedRequest req, UUID userId, String userName) {
        Incident i = load(tenantId, id);
        guarded(() -> { i.recordSubjectsNotified(req.notifiedAt()); return null; });
        incidents.save(i);
        trail.record(tenantId.getValue(), ENTITY, id, "SUBJECTS_NOTIFIED", "On " + req.notifiedAt(), userId, userName);
        return view(tenantId, i);
    }

    @Transactional
    public IncidentResponse deferSubjects(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, String userName) {
        Incident i = load(tenantId, id);
        guarded(() -> { i.deferSubjectsNotification(req.reason()); return null; });
        incidents.save(i);
        trail.record(tenantId.getValue(), ENTITY, id, "SUBJECTS_NOT_NOTIFIED", req.reason().trim(), userId, userName);
        return view(tenantId, i);
    }

    @Transactional
    public IncidentResponse close(TenantId tenantId, UUID id, FindingNoteRequest req, UUID userId, String userName) {
        Incident i = load(tenantId, id);
        if (userId != null && userId.equals(i.getInvestigatorId()))
            throw new HandyFlowException("Segregation of duties: you investigated this incident, so you cannot close it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
        long blocking = AssuranceRules.blockingCapas(capas.findByIncident(tenantId, id).stream().map(Capa::getStatus).toList());
        if (blocking > 0) throw new HandyFlowException(blocking + " corrective/preventive action(s) on this incident have not been verified yet", HttpStatus.CONFLICT, "CAPA_INCOMPLETE");
        guarded(() -> { i.close(userId, userName, req.note()); return null; });
        incidents.save(i);
        trail.record(tenantId.getValue(), ENTITY, id, "CLOSED", req.note().trim(), userId, userName);
        return view(tenantId, i);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> list(TenantId tenantId, IncidentStatus status, boolean includeClosed) { return incidents.search(tenantId, status, includeClosed).stream().map(i -> view(tenantId, i)).toList(); }

    @Transactional(readOnly = true)
    public IncidentResponse get(TenantId tenantId, UUID id) { return view(tenantId, load(tenantId, id)); }

    @Transactional(readOnly = true)
    public IncidentSummary summary(TenantId tenantId) {
        List<Incident> open = incidents.search(tenantId, null, false);
        return new IncidentSummary(open.size(), open.stream().filter(i -> i.getSeverity() == FindingSeverity.CRITICAL).count(), open.stream().filter(i -> i.getSeverity() == FindingSeverity.HIGH).count(),
                open.stream().filter(i -> i.isPersonalInfoInvolved() && i.getNotificationAssessedAt() == null).count(),
                open.stream().filter(i -> Boolean.TRUE.equals(i.getNotificationRequired()) && (i.getRegulatorNotifiedAt() == null || (i.getSubjectsNotifiedAt() == null && i.getSubjectsNotNotifiedReason() == null))).count(),
                open.stream().filter(i -> i.getStatus() == IncidentStatus.RESOLVED).count());
    }

    // ── internals ──

    Incident load(TenantId tenantId, UUID id) { return incidents.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Incident", id.toString())); }

    private void validateControl(TenantId tenantId, UUID controlId) {
        if (controlId != null) controls.findForTenant(tenantId, controlId).orElseThrow(() -> new ResourceNotFoundException("Control", controlId.toString()));
    }

    private IncidentResponse view(TenantId tenantId, Incident i) {
        List<CapaStatus> statuses = capas.findByIncident(tenantId, i.getId()).stream().map(Capa::getStatus).toList();
        int live = (int) statuses.stream().filter(s -> s != CapaStatus.CANCELLED).count();
        int verified = (int) statuses.stream().filter(s -> s == CapaStatus.VERIFIED).count();
        String blocker = i.isOpen() ? i.closeBlocker() : null;
        if (i.isOpen() && blocker == null && AssuranceRules.blockingCapas(statuses) > 0) blocker = AssuranceRules.blockingCapas(statuses) + " corrective/preventive action(s) have not been verified yet";
        String control = i.getControlId() == null ? null : controls.findForTenant(tenantId, i.getControlId()).map(c -> c.getReference() + " — " + c.getTitle()).orElse(null);
        return new IncidentResponse(i.getId(), i.getReference(), i.getIncidentType(), i.getTitle(), i.getDescription(), i.getSeverity(), i.getStatus(), i.getOccurredAt(), i.getDiscoveredAt(),
                i.getReportedByName(), i.getControlId(), control, i.isPersonalInfoInvolved(), i.getDataSubjectsAffected(), i.getDataCategories(), i.getInvestigatorName(),
                i.getContainmentActions(), i.getRootCause(), i.getResolutionNote(), i.getResolvedAt(), i.getNotificationAssessedAt(), i.getNotificationAssessedBy(), i.getNotificationRequired(),
                i.getNotificationRationale(), i.getRegulatorNotifiedAt(), i.getRegulatorReference(), i.getSubjectsNotifiedAt(), i.getSubjectsNotNotifiedReason(), i.getClosedByName(),
                i.getClosedAt(), i.getClosureNote(), i.isOpen() && GovernanceRules.assessmentOverdue(i.getDiscoveredAt(), i.isPersonalInfoInvolved(), i.getNotificationAssessedAt() != null, Instant.now()),
                live, verified, blocker);
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
