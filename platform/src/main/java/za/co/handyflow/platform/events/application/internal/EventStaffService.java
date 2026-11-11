package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventStaffAssignment;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.domain.repository.EventStaffRepository;
import za.co.handyflow.platform.events.dto.StaffAssignmentRequest;
import za.co.handyflow.platform.events.dto.StaffAssignmentResponse;
import za.co.handyflow.platform.events.dto.StaffCoverageRow;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * The staff and volunteer roster — who is where, when, and whether they've turned up.
 * <p>
 * CLASH DETECTION covers platform users only. When an assignment carries a userId, the person can't be
 * put on a shift that overlaps another live shift of theirs, in this event or any other event of the
 * tenant. People without a userId (most volunteers) can't be told apart reliably — two "Sipho"s are not
 * the same Sipho — so no clash check is attempted for them, rather than guessing and blocking the wrong
 * person. The cross-event check isn't serialised by a lock (the event lock is per event), so two
 * simultaneous assignments in different events could both slip through; that needs a unique constraint
 * on overlapping ranges to close fully and is rare enough to leave to the roster review.
 * <p>
 * Audit lines name the ROLE and KIND, never the person: the audit log is long-lived and shouldn't
 * become another copy of staff personal details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventStaffService {

    private final EventRepository      eventRepo;
    private final EventStaffRepository staffRepo;
    private final EventAuditService    audit;

    @Transactional(readOnly = true)
    public List<StaffAssignmentResponse> list(TenantId tenantId, UUID eventId, String status) {
        findEvent(tenantId, eventId);
        String wanted = (status == null || status.isBlank()) ? null : status.trim().toUpperCase(Locale.ROOT);
        return staffRepo.findByEvent(eventId).stream()
                .filter(a -> wanted == null || wanted.equals(a.getStatus()))
                .map(this::toResponse).toList();
    }

    /** Rostered / confirmed / arrived / no-show per role — "do we have enough stewards, and have they come?" */
    @Transactional(readOnly = true)
    public List<StaffCoverageRow> coverage(TenantId tenantId, UUID eventId) {
        findEvent(tenantId, eventId);
        Map<String, int[]> byRole = new TreeMap<>();      // [rostered, confirmed, arrived, noShow]
        for (EventStaffAssignment a : staffRepo.findByEvent(eventId)) {
            int[] c = byRole.computeIfAbsent(a.getRole(), r -> new int[4]);
            switch (a.getStatus()) {
                case EventStaffAssignment.ASSIGNED -> c[0]++;
                case EventStaffAssignment.CONFIRMED -> { c[0]++; c[1]++; }
                case EventStaffAssignment.ARRIVED -> { c[0]++; c[1]++; c[2]++; }
                case EventStaffAssignment.NO_SHOW -> c[3]++;
                default -> { /* cancelled shifts count for nothing */ }
            }
        }
        List<StaffCoverageRow> rows = new ArrayList<>();
        byRole.forEach((role, c) -> rows.add(new StaffCoverageRow(role, c[0], c[1], c[2], c[3])));
        return rows;
    }

    @Transactional
    public StaffAssignmentResponse add(TenantId tenantId, UUID eventId, StaffAssignmentRequest req) {
        Event event = lockEvent(tenantId, eventId);
        assertOpen(event);

        EventStaffAssignment a = EventStaffAssignment.create(tenantId, eventId, req.userId(), req.fullName(),
                req.kind(), req.role(), req.phone(), req.email(), req.post(), req.shiftStart(), req.shiftEnd(), req.notes());
        assertNoClash(a);

        staffRepo.save(a);
        audit.record(tenantId, eventId, "STAFF_ASSIGNED", "STAFF", a.getId(),
                "Rostered: " + a.getRole() + " (" + a.getKind() + "), shift " + a.getShiftStart() + " to " + a.getShiftEnd());
        return toResponse(a);
    }

    @Transactional
    public StaffAssignmentResponse update(TenantId tenantId, UUID eventId, UUID assignmentId, StaffAssignmentRequest req) {
        Event event = lockEvent(tenantId, eventId);
        assertOpen(event);
        EventStaffAssignment a = find(eventId, assignmentId);

        a.update(req.userId(), req.fullName(), req.kind(), req.role(), req.phone(), req.email(), req.post(),
                req.shiftStart(), req.shiftEnd(), req.notes());
        assertNoClash(a);

        staffRepo.save(a);
        audit.record(tenantId, eventId, "STAFF_UPDATED", "STAFF", a.getId(),
                "Roster entry updated: " + a.getRole() + " (" + a.getKind() + ")");
        return toResponse(a);
    }

    @Transactional
    public StaffAssignmentResponse confirm(TenantId tenantId, UUID eventId, UUID assignmentId) {
        lockEvent(tenantId, eventId);
        EventStaffAssignment a = find(eventId, assignmentId);
        a.confirm();
        staffRepo.save(a);
        audit.record(tenantId, eventId, "STAFF_CONFIRMED", "STAFF", a.getId(), "Shift confirmed: " + a.getRole());
        return toResponse(a);
    }

    @Transactional
    public StaffAssignmentResponse arrive(TenantId tenantId, UUID eventId, UUID assignmentId) {
        lockEvent(tenantId, eventId);
        EventStaffAssignment a = find(eventId, assignmentId);
        a.arrive();
        staffRepo.save(a);
        audit.record(tenantId, eventId, "STAFF_ARRIVED", "STAFF", a.getId(), "Arrived for shift: " + a.getRole());
        return toResponse(a);
    }

    @Transactional
    public StaffAssignmentResponse noShow(TenantId tenantId, UUID eventId, UUID assignmentId) {
        lockEvent(tenantId, eventId);
        EventStaffAssignment a = find(eventId, assignmentId);
        a.markNoShow();
        staffRepo.save(a);
        audit.record(tenantId, eventId, "STAFF_NO_SHOW", "STAFF", a.getId(), "No-show for shift: " + a.getRole());
        return toResponse(a);
    }

    @Transactional
    public StaffAssignmentResponse cancel(TenantId tenantId, UUID eventId, UUID assignmentId) {
        lockEvent(tenantId, eventId);
        EventStaffAssignment a = find(eventId, assignmentId);
        a.cancel();
        staffRepo.save(a);
        audit.record(tenantId, eventId, "STAFF_CANCELLED", "STAFF", a.getId(), "Shift cancelled: " + a.getRole());
        return toResponse(a);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Refuses a shift that overlaps another live shift of the same platform user, in any event of the tenant. */
    private void assertNoClash(EventStaffAssignment a) {
        if (a.getUserId() == null) return;
        List<EventStaffAssignment> clashes = staffRepo.findClashes(
                a.getTenantId(), a.getUserId(), a.getShiftStart(), a.getShiftEnd(), a.getId());
        if (!clashes.isEmpty()) {
            boolean sameEvent = clashes.stream().anyMatch(c -> c.getEventId().equals(a.getEventId()));
            throw new HandyFlowException(
                    "This person is already rostered for an overlapping shift"
                            + (sameEvent ? " at this event" : " at another event")
                            + " (" + clashes.get(0).getShiftStart() + " to " + clashes.get(0).getShiftEnd() + ")",
                    HttpStatus.CONFLICT, "STAFF_CLASH");
        }
    }

    private void assertOpen(Event event) {
        if ("COMPLETED".equals(event.getStatus()) || "CANCELLED".equals(event.getStatus())) {
            throw new HandyFlowException("The roster can't be changed for a " + event.getStatus() + " event",
                    HttpStatus.CONFLICT, "EVENT_CLOSED");
        }
    }

    private Event findEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private Event lockEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveByIdForUpdate(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private EventStaffAssignment find(UUID eventId, UUID assignmentId) {
        return staffRepo.findByIdAndEvent(assignmentId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Roster entry", assignmentId.toString()));
    }

    private StaffAssignmentResponse toResponse(EventStaffAssignment a) {
        return new StaffAssignmentResponse(a.getId(), a.getUserId(), a.getFullName(), a.getKind(), a.getRole(),
                a.getPhone(), a.getEmail(), a.getPost(), a.getShiftStart(), a.getShiftEnd(), a.getStatus(),
                a.getArrivedAt(), a.getNotes());
    }
}
