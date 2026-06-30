// security/application/internal/ControlRoomService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.domain.model.Dispatch;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.repository.AlarmEventRepository;
import za.co.handyflow.platform.security.domain.repository.DispatchRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * ControlRoomService — alarm event ingestion, triage, armed-response dispatch,
 * and SLA tracking. This is the command center tying Sites + Guards +
 * Incidents + Armed Response together, per Part 3 of the original audit.
 *
 * Flow:
 *   1. ingest() — webhook or manual entry creates a NEW AlarmEvent
 *   2. triage() — control room operator reviews, sets/confirms severity → TRIAGED
 *   3. dispatch() — creates a Dispatch row, marks the event DISPATCHED
 *   4. recordArrival() — response unit reports on-scene (response-time SLA)
 *   5. resolveDispatch() — closes the dispatch (resolution-time SLA)
 *      → if outcome is RESOLVED or ESCALATED, auto-creates an Incident
 *        linking back to the alarm event, so the full chain (alarm → dispatch
 *        → incident) is traceable from a single starting point.
 *
 * WHY auto-create an Incident on resolution rather than requiring a manual step?
 * An armed-response dispatch that resolved something real (not a false alarm)
 * IS a security incident by definition — making the operator separately file
 * an incident report duplicates work and risks the two records drifting out
 * of sync. FALSE_ALARM and NO_ACTION_NEEDED outcomes deliberately do NOT
 * create an incident, since nothing actually happened.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlRoomService {

    private final AlarmEventRepository eventRepository;
    private final DispatchRepository   dispatchRepository;
    private final GuardRepository      guardRepository;
    private final IncidentRepository   incidentRepository;

    // ── Ingestion ──────────────────────────────────────────────────────────────

    /**
     * Ingests a new alarm event — called from a webhook endpoint (alarm panel,
     * CCTV system) or manually by a control room operator.
     *
     * WHY no validation that siteId belongs to this tenant?
     * Some sources (a guard's duress button, a roaming drone) may not have a
     * fixed site association at ingestion time — siteId is intentionally
     * nullable. When present, downstream queries (findBySite) naturally scope
     * correctly since the row itself carries tenantId regardless.
     */
    @Transactional
    public AlarmEvent ingest(TenantId tenantId, IngestAlarmEventRequest req) {
        AlarmEvent.AlarmSource source;
        try {
            source = AlarmEvent.AlarmSource.valueOf(req.source());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid source: " + req.source(),
                    HttpStatus.BAD_REQUEST, "INVALID_ALARM_SOURCE");
        }

        AlarmEvent.AlarmSeverity severity = null;
        if (req.severity() != null) {
            try {
                severity = AlarmEvent.AlarmSeverity.valueOf(req.severity());
            } catch (IllegalArgumentException e) {
                throw new HandyFlowException("Invalid severity: " + req.severity(),
                        HttpStatus.BAD_REQUEST, "INVALID_SEVERITY");
            }
        }

        AlarmEvent event = AlarmEvent.ingest(
                tenantId, req.siteId(), source, req.rawPayload(), severity,
                req.triggeredByGuardId(), req.latitude(), req.longitude(),
                req.description());
        eventRepository.save(event);

        log.info("[Security] Alarm event ingested id={} source={} site={} severity={}",
                event.getId(), source, req.siteId(), event.getSeverity());

        return event;
    }

    // ── Triage Queue ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AlarmEvent> getOpenQueue(TenantId tenantId, Pageable pageable) {
        return eventRepository.findOpenQueue(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AlarmEvent> getEventsForSite(TenantId tenantId, UUID siteId, Pageable pageable) {
        return eventRepository.findBySite(tenantId, siteId, pageable);
    }

    @Transactional
    public AlarmEvent triage(TenantId tenantId, UUID eventId, UUID operatorId,
                             TriageAlarmEventRequest req) {
        AlarmEvent event = findEvent(tenantId, eventId);

        AlarmEvent.AlarmSeverity severity = null;
        if (req.severity() != null) {
            try {
                severity = AlarmEvent.AlarmSeverity.valueOf(req.severity());
            } catch (IllegalArgumentException e) {
                throw new HandyFlowException("Invalid severity: " + req.severity(),
                        HttpStatus.BAD_REQUEST, "INVALID_SEVERITY");
            }
        }

        event.triage(operatorId, severity);
        eventRepository.save(event);

        log.info("[Security] Alarm event triaged id={} by={} severity={}",
                eventId, operatorId, event.getSeverity());
        return event;
    }

    @Transactional
    public AlarmEvent markFalseAlarm(TenantId tenantId, UUID eventId) {
        AlarmEvent event = findEvent(tenantId, eventId);
        event.markFalseAlarm();
        eventRepository.save(event);
        return event;
    }

    // ── Dispatch ───────────────────────────────────────────────────────────────

    @Transactional
    public DispatchResponse dispatch(TenantId tenantId, UUID eventId, UUID operatorId,
                                     CreateDispatchRequest req) {
        AlarmEvent event = findEvent(tenantId, eventId);

        Dispatch.DispatchedUnitType unitType;
        try {
            unitType = Dispatch.DispatchedUnitType.valueOf(req.dispatchedUnitType());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid dispatchedUnitType: " + req.dispatchedUnitType(),
                    HttpStatus.BAD_REQUEST, "INVALID_UNIT_TYPE");
        }

        // Validate the dispatched guard belongs to this tenant, if supplied
        if (req.dispatchedGuardId() != null) {
            guardRepository.findActiveById(tenantId, req.dispatchedGuardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Guard",
                            req.dispatchedGuardId().toString()));
        }

        Dispatch dispatchRecord = Dispatch.create(
                tenantId, eventId, unitType, req.dispatchedGuardId(), operatorId);
        dispatchRepository.save(dispatchRecord);

        event.markDispatched();
        eventRepository.save(event);

        log.info("[Security] Dispatch created eventId={} unitType={} guard={} by={}",
                eventId, unitType, req.dispatchedGuardId(), operatorId);

        return toDispatchResponse(dispatchRecord, tenantId);
    }

    @Transactional
    public DispatchResponse recordArrival(TenantId tenantId, UUID dispatchId) {
        Dispatch dispatchRecord = findDispatch(tenantId, dispatchId);
        dispatchRecord.recordArrival();
        dispatchRepository.save(dispatchRecord);

        log.info("[Security] Dispatch arrival recorded id={} responseTime={}min",
                dispatchId, dispatchRecord.responseTimeMinutes());
        return toDispatchResponse(dispatchRecord, tenantId);
    }

    /**
     * Resolves a dispatch and, if the outcome indicates something real
     * happened (RESOLVED or ESCALATED), auto-creates a linked Incident.
     *
     * WHY does the auto-created Incident use the event's site/severity rather
     * than asking the operator to re-enter them?
     * The alarm event already carries this context — re-asking the operator
     * to type the same site and severity into a separate incident form is
     * exactly the duplicated-data-entry friction this auto-linkage exists
     * to eliminate.
     */
    @Transactional
    public DispatchResponse resolveDispatch(TenantId tenantId, UUID dispatchId,
                                            ResolveDispatchRequest req) {
        Dispatch dispatchRecord = findDispatch(tenantId, dispatchId);

        Dispatch.DispatchOutcome outcome;
        try {
            outcome = Dispatch.DispatchOutcome.valueOf(req.outcome());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid outcome: " + req.outcome(),
                    HttpStatus.BAD_REQUEST, "INVALID_OUTCOME");
        }

        dispatchRecord.resolve(outcome, req.notes());
        dispatchRepository.save(dispatchRecord);

        AlarmEvent event = findEvent(tenantId, dispatchRecord.getAlarmEventId());

        UUID linkedIncidentId = null;
        if (outcome == Dispatch.DispatchOutcome.RESOLVED
                || outcome == Dispatch.DispatchOutcome.ESCALATED) {
            linkedIncidentId = createLinkedIncident(tenantId, event, dispatchRecord, outcome);
        }

        event.resolve(linkedIncidentId);
        eventRepository.save(event);

        log.info("[Security] Dispatch resolved id={} outcome={} resolutionTime={}min incident={}",
                dispatchId, outcome, dispatchRecord.resolutionTimeMinutes(), linkedIncidentId);

        return toDispatchResponse(dispatchRecord, tenantId);
    }

    /**
     * Creates the linked Incident from a resolved/escalated dispatch.
     * siteId on Incident is NOT NULL in the schema (V12), so a dispatch
     * for a site-less event (e.g. a roaming duress trigger) falls back to
     * raising the exception rather than writing an invalid row — control
     * room operators handling site-less events should file the incident
     * manually with the appropriate site context.
     */
    private UUID createLinkedIncident(TenantId tenantId, AlarmEvent event,
                                      Dispatch dispatchRecord,
                                      Dispatch.DispatchOutcome outcome) {
        if (event.getSiteId() == null) {
            log.warn("[Security] Cannot auto-create incident for site-less alarm event={} " +
                    "— file manually", event.getId());
            return null;
        }

        String title = "Armed response " + outcome.name().toLowerCase()
                + " — " + event.getSource().name().replace('_', ' ').toLowerCase();
        String description = "Auto-created from alarm event " + event.getId()
                + " (source: " + event.getSource() + "). "
                + (dispatchRecord.getResolutionNotes() != null
                ? dispatchRecord.getResolutionNotes() : "");

        Incident incident = Incident.create(
                tenantId, event.getSiteId(), null, event.getTriggeredByGuardId(),
                title, description, event.getSeverity().name(),
                event.getLatitude(), event.getLongitude());
        incidentRepository.save(incident);

        return incident.getId();
    }

    // ── Duress Trigger (Part 9.2/9.4) ─────────────────────────────────────────

    /**
     * Triggers a duress event — the highest-priority alarm source in the
     * pipeline. Not a new table: this is exactly the same AlarmEvent.ingest()
     * call as everything else in the control room, with source=DURESS and
     * severity hard-set to CRITICAL regardless of any caller input, plus an
     * optional link to the protection detail the duress occurred on.
     *
     * WHY hard-set CRITICAL rather than accepting a severity parameter?
     * Part 9.4 specifies duress as bypassing "the normal severity/description
     * form entirely" for sub-second alerting — there is no scenario where a
     * duress trigger should be anything other than the highest priority in
     * the triage queue. Removing the choice removes a failure mode where a
     * panicked guard or a buggy client accidentally under-prioritizes their
     * own panic signal.
     *
     * WHY no triage step required before dispatch?
     * Unlike other alarm sources, a duress event going through NEW → TRIAGED
     * → DISPATCHED would add a human-in-the-loop delay that defeats the
     * point. Callers (the control room UI) should dispatch immediately on
     * seeing a DURESS-source event in the queue rather than waiting for a
     * separate triage action — this method doesn't auto-dispatch (that still
     * requires an explicit dispatch() call with a chosen unit), but it does
     * skip straight past the optional triage step by not requiring one.
     */
    @Transactional
    public AlarmEvent triggerDuress(TenantId tenantId, TriggerDuressRequest req) {
        IngestAlarmEventRequest alarmReq = new IngestAlarmEventRequest(
                null, "DURESS", null, "CRITICAL", req.triggeredByGuardId(),
                req.latitude(), req.longitude(), "DURESS TRIGGER — immediate response required");

        AlarmEvent event = ingest(tenantId, alarmReq);

        if (req.protectionDetailId() != null) {
            event.linkProtectionDetail(req.protectionDetailId());
            eventRepository.save(event);
        }

        log.warn("[Security] DURESS TRIGGERED eventId={} guard={} detail={}",
                event.getId(), req.triggeredByGuardId(), req.protectionDetailId());

        return event;
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Dispatch> getOpenDispatches(TenantId tenantId) {
        return dispatchRepository.findOpen(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Dispatch> getDispatchesForEvent(UUID alarmEventId) {
        return dispatchRepository.findByAlarmEvent(alarmEventId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AlarmEvent findEvent(TenantId tenantId, UUID id) {
        return eventRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("AlarmEvent", id.toString()));
    }

    private Dispatch findDispatch(TenantId tenantId, UUID id) {
        return dispatchRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch", id.toString()));
    }

    private DispatchResponse toDispatchResponse(Dispatch d, TenantId tenantId) {
        String guardName = d.getDispatchedGuardId() != null
                ? guardRepository.findActiveById(tenantId, d.getDispatchedGuardId())
                .map(Guard::getFullName).orElse("Unknown")
                : null;

        return new DispatchResponse(
                d.getId(), d.getAlarmEventId(), d.getDispatchedUnitType().name(),
                d.getDispatchedGuardId(), guardName, d.getDispatchedBy(),
                d.getDispatchedAt(), d.getArrivedAt(), d.getResolvedAt(),
                d.responseTimeMinutes(), d.resolutionTimeMinutes(),
                d.getOutcome() != null ? d.getOutcome().name() : null,
                d.getResolutionNotes(), d.isOpen());
    }
}
