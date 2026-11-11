package za.co.handyflow.platform.events.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.events.application.internal.EventIncidentService;
import za.co.handyflow.platform.events.application.internal.EventPlaybookService;
import za.co.handyflow.platform.events.application.internal.EventSessionService;
import za.co.handyflow.platform.events.application.internal.EventStaffService;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Event-day operations: run sheet, staff roster, incidents, emergency contacts, checklist and readiness.
 * Shares the /api/v1/events base path with the other event controllers.
 * <p>
 * WHO CAN SEE WHAT: the programme and checklists are ordinary event information (EVENTS_READ). The
 * roster, incidents and emergency contacts need an operations permission, because they carry staff phone
 * numbers and accounts of things that went wrong. Reporting an incident is deliberately open to door
 * staff, since they are often the first to see one.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event operations", description = "Run sheet, staff roster, incidents, emergency contacts, checklists and readiness")
public class EventOpsController {

    private final EventSessionService  sessions;
    private final EventStaffService    staff;
    private final EventIncidentService incidents;
    private final EventPlaybookService playbook;

    // ── Run sheet ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}/sessions")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "The programme in time order")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> listSessions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                sessions.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/run-sheet")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "Live run sheet: the programme with the current session, the next one and how late the day is running. Cheap to poll.")
    public ResponseEntity<ApiResponse<RunSheetResponse>> runSheet(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                sessions.runSheet(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/sessions")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Add a session — refused if it double-books a room or a speaker, or falls outside the event (setup/breakdown excepted)")
    public ResponseEntity<ApiResponse<SessionResponse>> addSession(
            @PathVariable UUID id, @Valid @RequestBody SessionRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Session added",
                sessions.create(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PutMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Edit a session that hasn't finished")
    public ResponseEntity<ApiResponse<SessionResponse>> updateSession(
            @PathVariable UUID id, @PathVariable UUID sessionId, @Valid @RequestBody SessionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Session updated",
                sessions.update(TenantContext.getTenantIdAsObject(), id, sessionId, req)));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Delete a session that never ran")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        sessions.delete(TenantContext.getTenantIdAsObject(), id, sessionId);
        return ResponseEntity.ok(ApiResponse.success("Session deleted", null));
    }

    @PostMapping("/{id}/sessions/{sessionId}/start")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Start a session — event must be LIVE and the room free; records the actual start time")
    public ResponseEntity<ApiResponse<SessionResponse>> startSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Session started",
                sessions.start(TenantContext.getTenantIdAsObject(), id, sessionId)));
    }

    @PostMapping("/{id}/sessions/{sessionId}/complete")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Complete a running session — records the actual end time")
    public ResponseEntity<ApiResponse<SessionResponse>> completeSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Session completed",
                sessions.complete(TenantContext.getTenantIdAsObject(), id, sessionId)));
    }

    @PostMapping("/{id}/sessions/{sessionId}/cancel")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Cancel a session")
    public ResponseEntity<ApiResponse<SessionResponse>> cancelSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.success("Session cancelled",
                sessions.cancel(TenantContext.getTenantIdAsObject(), id, sessionId)));
    }

    @PostMapping("/{id}/sessions/shift")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Running late? Move every not-yet-started session from a point onward by N minutes — refused if it would cause a clash")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> shiftSessions(
            @PathVariable UUID id, @Valid @RequestBody ShiftSessionsRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Sessions shifted",
                sessions.shift(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Staff roster ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/staff")
    @PreAuthorize(EventsPermissions.OPS_READ)
    @Operation(summary = "The roster — also the on-the-day contact list. Optional status filter.")
    public ResponseEntity<ApiResponse<List<StaffAssignmentResponse>>> listStaff(
            @PathVariable UUID id, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                staff.list(TenantContext.getTenantIdAsObject(), id, status)));
    }

    @GetMapping("/{id}/staff/coverage")
    @PreAuthorize(EventsPermissions.OPS_READ)
    @Operation(summary = "Per role: rostered, confirmed, arrived and no-shows")
    public ResponseEntity<ApiResponse<List<StaffCoverageRow>>> staffCoverage(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                staff.coverage(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/staff")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Roster a staff member, volunteer or contractor — a platform user can't be double-booked, even across events")
    public ResponseEntity<ApiResponse<StaffAssignmentResponse>> addStaff(
            @PathVariable UUID id, @Valid @RequestBody StaffAssignmentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Rostered",
                staff.add(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PutMapping("/{id}/staff/{assignmentId}")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Edit a roster entry that hasn't started")
    public ResponseEntity<ApiResponse<StaffAssignmentResponse>> updateStaff(
            @PathVariable UUID id, @PathVariable UUID assignmentId, @Valid @RequestBody StaffAssignmentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Roster entry updated",
                staff.update(TenantContext.getTenantIdAsObject(), id, assignmentId, req)));
    }

    @PostMapping("/{id}/staff/{assignmentId}/confirm")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Mark a shift confirmed")
    public ResponseEntity<ApiResponse<StaffAssignmentResponse>> confirmStaff(@PathVariable UUID id, @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(ApiResponse.success("Shift confirmed",
                staff.confirm(TenantContext.getTenantIdAsObject(), id, assignmentId)));
    }

    @PostMapping("/{id}/staff/{assignmentId}/arrive")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Mark a person as arrived for their shift")
    public ResponseEntity<ApiResponse<StaffAssignmentResponse>> arriveStaff(@PathVariable UUID id, @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(ApiResponse.success("Arrival recorded",
                staff.arrive(TenantContext.getTenantIdAsObject(), id, assignmentId)));
    }

    @PostMapping("/{id}/staff/{assignmentId}/no-show")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Mark a rostered person as a no-show")
    public ResponseEntity<ApiResponse<StaffAssignmentResponse>> noShowStaff(@PathVariable UUID id, @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(ApiResponse.success("No-show recorded",
                staff.noShow(TenantContext.getTenantIdAsObject(), id, assignmentId)));
    }

    @PostMapping("/{id}/staff/{assignmentId}/cancel")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Cancel a shift")
    public ResponseEntity<ApiResponse<StaffAssignmentResponse>> cancelStaff(@PathVariable UUID id, @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(ApiResponse.success("Shift cancelled",
                staff.cancel(TenantContext.getTenantIdAsObject(), id, assignmentId)));
    }

    // ── Incidents ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/incidents")
    @PreAuthorize(EventsPermissions.INCIDENT_REPORT)
    @Operation(summary = "Report an incident — open to door staff and stewards, not just operations managers")
    public ResponseEntity<ApiResponse<IncidentResponse>> reportIncident(
            @PathVariable UUID id, @Valid @RequestBody ReportIncidentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Incident reported",
                incidents.report(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/{id}/incidents")
    @PreAuthorize(EventsPermissions.OPS_READ)
    @Operation(summary = "List incidents, newest first. activeOnly hides resolved and closed ones. Overdue flags are worked out on every read.")
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> listIncidents(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) String severity,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                incidents.list(TenantContext.getTenantIdAsObject(), id, activeOnly, severity, pageable)));
    }

    @GetMapping("/{id}/incidents/summary")
    @PreAuthorize(EventsPermissions.OPS_READ)
    @Operation(summary = "Counts for the command-centre header: unresolved by severity and status, and how many are overdue")
    public ResponseEntity<ApiResponse<IncidentSummaryResponse>> incidentSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                incidents.summary(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/incidents/{incidentId}")
    @PreAuthorize(EventsPermissions.OPS_READ)
    @Operation(summary = "One incident with its full timeline")
    public ResponseEntity<ApiResponse<IncidentResponse>> getIncident(@PathVariable UUID id, @PathVariable UUID incidentId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                incidents.get(TenantContext.getTenantIdAsObject(), id, incidentId)));
    }

    @PostMapping("/{id}/incidents/{incidentId}/notes")
    @PreAuthorize(EventsPermissions.INCIDENT_REPORT)
    @Operation(summary = "Add a note to an incident's timeline")
    public ResponseEntity<ApiResponse<IncidentNoteResponse>> addIncidentNote(
            @PathVariable UUID id, @PathVariable UUID incidentId, @Valid @RequestBody IncidentNoteRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Note added",
                incidents.addNote(TenantContext.getTenantIdAsObject(), id, incidentId, req.note())));
    }

    @PostMapping("/{id}/incidents/{incidentId}/acknowledge")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Take an incident without handing it on — stops the 'nobody has seen this' clock")
    public ResponseEntity<ApiResponse<IncidentResponse>> acknowledgeIncident(@PathVariable UUID id, @PathVariable UUID incidentId) {
        return ResponseEntity.ok(ApiResponse.success("Incident acknowledged",
                incidents.acknowledge(TenantContext.getTenantIdAsObject(), id, incidentId)));
    }

    @PostMapping("/{id}/incidents/{incidentId}/assign")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Assign an incident to someone")
    public ResponseEntity<ApiResponse<IncidentResponse>> assignIncident(
            @PathVariable UUID id, @PathVariable UUID incidentId, @Valid @RequestBody AssignIncidentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Incident assigned",
                incidents.assign(TenantContext.getTenantIdAsObject(), id, incidentId, req)));
    }

    @PostMapping("/{id}/incidents/{incidentId}/escalate")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Escalate up the chain (levels 1-3), with a reason; may raise severity, never lower it")
    public ResponseEntity<ApiResponse<IncidentResponse>> escalateIncident(
            @PathVariable UUID id, @PathVariable UUID incidentId, @Valid @RequestBody EscalateIncidentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Incident escalated",
                incidents.escalate(TenantContext.getTenantIdAsObject(), id, incidentId, req)));
    }

    @PostMapping("/{id}/incidents/{incidentId}/resolve")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Resolve an incident — the resolution must say how")
    public ResponseEntity<ApiResponse<IncidentResponse>> resolveIncident(
            @PathVariable UUID id, @PathVariable UUID incidentId, @Valid @RequestBody ResolveIncidentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Incident resolved",
                incidents.resolve(TenantContext.getTenantIdAsObject(), id, incidentId, req.resolution())));
    }

    @PostMapping("/{id}/incidents/{incidentId}/close")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Close a resolved incident")
    public ResponseEntity<ApiResponse<IncidentResponse>> closeIncident(@PathVariable UUID id, @PathVariable UUID incidentId) {
        return ResponseEntity.ok(ApiResponse.success("Incident closed",
                incidents.close(TenantContext.getTenantIdAsObject(), id, incidentId)));
    }

    @PostMapping("/{id}/incidents/{incidentId}/reopen")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Reopen a resolved or closed incident — a reason is required")
    public ResponseEntity<ApiResponse<IncidentResponse>> reopenIncident(
            @PathVariable UUID id, @PathVariable UUID incidentId, @Valid @RequestBody IncidentReasonRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Incident reopened",
                incidents.reopen(TenantContext.getTenantIdAsObject(), id, incidentId, req.reason())));
    }

    // ── Emergency contacts ────────────────────────────────────────────────────

    @GetMapping("/{id}/contacts")
    @PreAuthorize(EventsPermissions.OPS_READ)
    @Operation(summary = "Emergency contacts")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> listContacts(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                playbook.listContacts(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/contacts")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Add an emergency contact — a phone number is required")
    public ResponseEntity<ApiResponse<ContactResponse>> addContact(
            @PathVariable UUID id, @Valid @RequestBody ContactRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Contact added",
                playbook.addContact(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PutMapping("/{id}/contacts/{contactId}")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Update an emergency contact")
    public ResponseEntity<ApiResponse<ContactResponse>> updateContact(
            @PathVariable UUID id, @PathVariable UUID contactId, @Valid @RequestBody ContactRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Contact updated",
                playbook.updateContact(TenantContext.getTenantIdAsObject(), id, contactId, req)));
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Remove an emergency contact")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable UUID id, @PathVariable UUID contactId) {
        playbook.deleteContact(TenantContext.getTenantIdAsObject(), id, contactId);
        return ResponseEntity.ok(ApiResponse.success("Contact removed", null));
    }

    // ── Checklist & contingency plans ─────────────────────────────────────────

    @GetMapping("/{id}/checklist")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "The checklist by phase. CONTINGENCY items are the contingency plans (done = reviewed).")
    public ResponseEntity<ApiResponse<List<ChecklistItemResponse>>> listChecklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                playbook.listChecklist(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/checklist")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Add a checklist item or contingency plan")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> addChecklistItem(
            @PathVariable UUID id, @Valid @RequestBody ChecklistItemRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Item added",
                playbook.addItem(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/checklist/from-template")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Add a starter checklist and starter contingency plans — safe to repeat; never duplicates or overwrites")
    public ResponseEntity<ApiResponse<ChecklistTemplateResult>> applyChecklistTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Template applied",
                playbook.applyTemplate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/{id}/checklist/{itemId}")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Edit a checklist item")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> updateChecklistItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody ChecklistItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Item updated",
                playbook.updateItem(TenantContext.getTenantIdAsObject(), id, itemId, req)));
    }

    @DeleteMapping("/{id}/checklist/{itemId}")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Remove a checklist item")
    public ResponseEntity<ApiResponse<Void>> deleteChecklistItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        playbook.deleteItem(TenantContext.getTenantIdAsObject(), id, itemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed", null));
    }

    @PostMapping("/{id}/checklist/{itemId}/complete")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Tick an item off (for a contingency plan: mark it reviewed). Safe to repeat.")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> completeChecklistItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.success("Item completed",
                playbook.completeItem(TenantContext.getTenantIdAsObject(), id, itemId)));
    }

    @PostMapping("/{id}/checklist/{itemId}/reopen")
    @PreAuthorize(EventsPermissions.OPS_MANAGE)
    @Operation(summary = "Untick an item")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> reopenChecklistItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.success("Item reopened",
                playbook.reopenItem(TenantContext.getTenantIdAsObject(), id, itemId)));
    }

    // ── Readiness ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}/readiness")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "Are we ready to run this event? Advisory — never blocks going live — but each failed check says exactly what to fix.")
    public ResponseEntity<ApiResponse<ReadinessResponse>> readiness(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                playbook.readiness(TenantContext.getTenantIdAsObject(), id)));
    }
}
