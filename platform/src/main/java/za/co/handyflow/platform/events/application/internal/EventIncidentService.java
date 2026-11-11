package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.model.EventIncident;
import za.co.handyflow.platform.events.domain.model.EventIncidentNote;
import za.co.handyflow.platform.events.domain.repository.EventIncidentNoteRepository;
import za.co.handyflow.platform.events.domain.repository.EventIncidentRepository;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Incident reporting, handling and escalation on the day.
 * <p>
 * CONCURRENCY: incidents carry @Version, so two people changing one incident at the same moment
 * (one resolving, one escalating) can't silently overwrite each other — the second gets a 409 and
 * reloads. No event-wide lock is taken: incident handling must never queue behind registrations or
 * budget edits during a live event.
 * <p>
 * REFERENCES: INC-001, INC-002 ... per event, from TenantSequenceService — atomic and duplicate-proof
 * without a lock, at the cost of an occasional gap if an incident fails to save after its number was
 * issued (gaps are explainable; a repeated reference is not).
 * <p>
 * NO PUSH ALERTS: nothing here can notify anyone (the Events module has no path to the notifications
 * module). Overdue acknowledgement and resolution are computed on every read from IncidentSla, and the
 * summary counts them, so a command-centre screen can show them prominently.
 * <p>
 * PRIVACY: incident text may describe guests, so it stays on the incident rows and is never copied into
 * the audit trail, whose lines carry only the reference, category and severity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventIncidentService {

    private final EventRepository             eventRepo;
    private final EventIncidentRepository     incidentRepo;
    private final EventIncidentNoteRepository noteRepo;
    private final TenantSequenceService       sequences;
    private final EventAuditService           audit;

    // ── Report ────────────────────────────────────────────────────────────────

    @Transactional
    public IncidentResponse report(TenantId tenantId, UUID eventId, ReportIncidentRequest req) {
        requireEvent(tenantId, eventId);

        String reference = String.format("INC-%03d", sequences.nextValue(tenantId, "INC:" + eventId));
        EventIncident i = EventIncident.report(tenantId, eventId, reference, req.title(), req.description(),
                req.category(), req.severity(), req.location(), actorId(), actorName());
        incidentRepo.save(i);

        timeline(tenantId, eventId, i.getId(), EventIncidentNote.STATUS,
                "Reported — " + i.getSeverity() + " " + i.getCategory());
        audit.record(tenantId, eventId, "INCIDENT_REPORTED", "INCIDENT", i.getId(),
                "Incident " + reference + " reported — " + i.getCategory() + ", " + i.getSeverity());
        log.info("[Events] Incident {} reported event={} severity={}", reference, eventId, i.getSeverity());
        return toResponse(i, null);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<IncidentResponse> list(TenantId tenantId, UUID eventId, boolean activeOnly,
                                       String severity, Pageable pageable) {
        requireEvent(tenantId, eventId);
        String sev = (severity == null || severity.isBlank()) ? null : severity.trim().toUpperCase(Locale.ROOT);
        return incidentRepo.search(eventId, activeOnly, sev, pageable).map(i -> toResponse(i, null));
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(TenantId tenantId, UUID eventId, UUID incidentId) {
        requireEvent(tenantId, eventId);
        EventIncident i = find(eventId, incidentId);
        List<IncidentNoteResponse> timeline = noteRepo.findTimeline(incidentId).stream()
                .map(n -> new IncidentNoteResponse(n.getId(), n.getKind(), n.getNote(), n.getAuthorName(), n.getCreatedAt()))
                .toList();
        return toResponse(i, timeline);
    }

    /** Counts for the command-centre header. "Overdue" means unresolved AND past its acknowledge or resolve target. */
    @Transactional(readOnly = true)
    public IncidentSummaryResponse summary(TenantId tenantId, UUID eventId) {
        requireEvent(tenantId, eventId);
        List<EventIncident> unresolved = incidentRepo.findUnresolved(eventId);
        long total = incidentRepo.countByEvent(eventId);

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        EventIncident.SEVERITIES.forEach(s -> bySeverity.put(s, 0L));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        List.of(EventIncident.OPEN, EventIncident.IN_PROGRESS, EventIncident.ESCALATED).forEach(s -> byStatus.put(s, 0L));

        long overdue = 0;
        Instant now = Instant.now();
        for (EventIncident i : unresolved) {
            bySeverity.merge(i.getSeverity(), 1L, Long::sum);
            byStatus.merge(i.getStatus(), 1L, Long::sum);
            IncidentSla.Assessment a = IncidentSla.assess(i.getSeverity(), i.getStatus(), i.getCreatedAt(),
                    i.getAcknowledgedAt(), i.getResolvedAt(), now);
            if (a.acknowledgementOverdue() || a.resolutionOverdue()) overdue++;
        }
        return new IncidentSummaryResponse(total, unresolved.size(), total - unresolved.size(), overdue, bySeverity, byStatus);
    }

    // ── Handle ────────────────────────────────────────────────────────────────

    @Transactional
    public IncidentNoteResponse addNote(TenantId tenantId, UUID eventId, UUID incidentId, String note) {
        requireEvent(tenantId, eventId);
        find(eventId, incidentId);
        EventIncidentNote n = timeline(tenantId, eventId, incidentId, EventIncidentNote.NOTE, note);
        return new IncidentNoteResponse(n.getId(), n.getKind(), n.getNote(), n.getAuthorName(), n.getCreatedAt());
    }

    @Transactional
    public IncidentResponse acknowledge(TenantId tenantId, UUID eventId, UUID incidentId) {
        requireEvent(tenantId, eventId);
        EventIncident i = find(eventId, incidentId);
        i.acknowledge();
        incidentRepo.save(i);
        timeline(tenantId, eventId, incidentId, EventIncidentNote.STATUS, "Acknowledged — being looked into");
        return toResponse(i, null);
    }

    @Transactional
    public IncidentResponse assign(TenantId tenantId, UUID eventId, UUID incidentId, AssignIncidentRequest req) {
        requireEvent(tenantId, eventId);
        EventIncident i = find(eventId, incidentId);
        i.assign(req.assigneeName(), req.assigneeUserId());
        incidentRepo.save(i);
        timeline(tenantId, eventId, incidentId, EventIncidentNote.ASSIGNMENT, "Assigned to " + i.getAssignedToName());
        audit.record(tenantId, eventId, "INCIDENT_ASSIGNED", "INCIDENT", incidentId,
                "Incident " + i.getReference() + " assigned");
        return toResponse(i, null);
    }

    @Transactional
    public IncidentResponse escalate(TenantId tenantId, UUID eventId, UUID incidentId, EscalateIncidentRequest req) {
        requireEvent(tenantId, eventId);
        EventIncident i = find(eventId, incidentId);
        String before = i.getSeverity();
        i.escalate(req.raiseSeverityTo());
        incidentRepo.save(i);

        timeline(tenantId, eventId, incidentId, EventIncidentNote.ESCALATION,
                "Escalated to level " + i.getEscalationLevel()
                        + (before.equals(i.getSeverity()) ? "" : " and severity raised " + before + " → " + i.getSeverity())
                        + " — " + req.reason().trim());
        audit.record(tenantId, eventId, "INCIDENT_ESCALATED", "INCIDENT", incidentId,
                "Incident " + i.getReference() + " escalated to level " + i.getEscalationLevel()
                        + " (" + i.getSeverity() + ")");
        log.warn("[Events] Incident {} escalated to level {} event={}", i.getReference(), i.getEscalationLevel(), eventId);
        return toResponse(i, null);
    }

    @Transactional
    public IncidentResponse resolve(TenantId tenantId, UUID eventId, UUID incidentId, String resolution) {
        requireEvent(tenantId, eventId);
        EventIncident i = find(eventId, incidentId);
        i.resolve(resolution);
        incidentRepo.save(i);
        timeline(tenantId, eventId, incidentId, EventIncidentNote.STATUS, "Resolved — " + i.getResolution());
        audit.record(tenantId, eventId, "INCIDENT_RESOLVED", "INCIDENT", incidentId,
                "Incident " + i.getReference() + " resolved");
        return toResponse(i, null);
    }

    @Transactional
    public IncidentResponse close(TenantId tenantId, UUID eventId, UUID incidentId) {
        requireEvent(tenantId, eventId);
        EventIncident i = find(eventId, incidentId);
        i.close();
        incidentRepo.save(i);
        timeline(tenantId, eventId, incidentId, EventIncidentNote.STATUS, "Closed");
        audit.record(tenantId, eventId, "INCIDENT_CLOSED", "INCIDENT", incidentId,
                "Incident " + i.getReference() + " closed");
        return toResponse(i, null);
    }

    @Transactional
    public IncidentResponse reopen(TenantId tenantId, UUID eventId, UUID incidentId, String reason) {
        requireEvent(tenantId, eventId);
        EventIncident i = find(eventId, incidentId);
        i.reopen();
        incidentRepo.save(i);
        timeline(tenantId, eventId, incidentId, EventIncidentNote.STATUS, "Reopened — " + reason.trim());
        audit.record(tenantId, eventId, "INCIDENT_REOPENED", "INCIDENT", incidentId,
                "Incident " + i.getReference() + " reopened");
        return toResponse(i, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Tenant check: confirms the event belongs to the caller's tenant before anything is read or changed. */
    private void requireEvent(TenantId tenantId, UUID eventId) {
        eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private EventIncident find(UUID eventId, UUID incidentId) {
        return incidentRepo.findByIdAndEvent(incidentId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId.toString()));
    }

    private EventIncidentNote timeline(TenantId tenantId, UUID eventId, UUID incidentId, String kind, String text) {
        return noteRepo.save(EventIncidentNote.of(tenantId, eventId, incidentId, kind, text, actorId(), actorName()));
    }

    private UUID actorId() {
        String raw = TenantContext.getUserId();
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String actorName() {
        return TenantContext.getUserId() == null ? "System" : TenantContext.getCurrentUserName();
    }

    private IncidentResponse toResponse(EventIncident i, List<IncidentNoteResponse> timeline) {
        IncidentSla.Assessment sla = IncidentSla.assess(i.getSeverity(), i.getStatus(), i.getCreatedAt(),
                i.getAcknowledgedAt(), i.getResolvedAt(), Instant.now());
        return new IncidentResponse(i.getId(), i.getReference(), i.getTitle(), i.getDescription(), i.getCategory(),
                i.getSeverity(), i.getStatus(), i.getLocation(), i.getReportedByName(), i.getAssignedToName(),
                i.getEscalationLevel(), i.getEscalatedAt(), i.getAcknowledgedAt(), i.getResolvedAt(),
                i.getResolution(), i.getCreatedAt(), sla.minutesOpen(), sla.acknowledgementOverdue(),
                sla.resolutionOverdue(), timeline);
    }
}
