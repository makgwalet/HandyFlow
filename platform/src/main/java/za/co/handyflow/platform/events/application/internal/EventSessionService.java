package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.application.internal.SessionLogic.RunItem;
import za.co.handyflow.platform.events.application.internal.SessionLogic.RunState;
import za.co.handyflow.platform.events.application.internal.SessionLogic.Slot;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventSession;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.domain.repository.EventSessionRepository;
import za.co.handyflow.platform.events.dto.RunSheetResponse;
import za.co.handyflow.platform.events.dto.SessionRequest;
import za.co.handyflow.platform.events.dto.SessionResponse;
import za.co.handyflow.platform.events.dto.ShiftSessionsRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The event's programme and its live run sheet.
 * <p>
 * Every change takes the event row lock first, so two people editing the programme at once can't both
 * pass the room-clash check and double-book a room. The clash rules themselves live in SessionLogic,
 * as pure functions.
 * <p>
 * A session is only ever STARTED while the event is LIVE, and never while another session is already
 * running in the same room — the run sheet describes what is physically happening on the day, and two
 * things can't be happening on one stage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventSessionService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final EventRepository        eventRepo;
    private final EventSessionRepository sessionRepo;
    private final EventAuditService      audit;

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SessionResponse> list(TenantId tenantId, UUID eventId) {
        findEvent(tenantId, eventId);
        return sessionRepo.findByEvent(eventId).stream().map(this::toResponse).toList();
    }

    /** The day's programme in time order, with the current session, the next one, and how late the day is running. */
    @Transactional(readOnly = true)
    public RunSheetResponse runSheet(TenantId tenantId, UUID eventId) {
        findEvent(tenantId, eventId);
        List<EventSession> sessions = sessionRepo.findByEvent(eventId);
        List<SessionResponse> responses = sessions.stream().map(this::toResponse).toList();
        Map<UUID, SessionResponse> byId = responses.stream()
                .collect(Collectors.toMap(SessionResponse::id, r -> r));

        RunState state = SessionLogic.runState(
                sessions.stream().map(s -> new RunItem(s.getId(), s.getStatus(), s.getStartAt(), s.getEndAt())).toList(),
                LocalDateTime.now(SAST));

        return new RunSheetResponse(Instant.now(), responses,
                state.currentId() != null ? byId.get(state.currentId()) : null,
                state.nextId() != null ? byId.get(state.nextId()) : null,
                state.runningLateMinutes());
    }

    // ── Create / edit / delete ────────────────────────────────────────────────

    @Transactional
    public SessionResponse create(TenantId tenantId, UUID eventId, SessionRequest req) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);

        EventSession s = EventSession.create(tenantId, eventId, req.title(), req.description(),
                req.sessionType(), req.room(), req.speaker(), req.startAt(), req.endAt(), req.notes());
        assertWithinEvent(event, s);
        assertNoConflicts(eventId, s);

        sessionRepo.save(s);
        audit.record(tenantId, eventId, "SESSION_ADDED", "SESSION", s.getId(),
                "Session added: " + s.getTitle() + " (" + s.getSessionType() + ")");
        return toResponse(s);
    }

    /**
     * Edits a session. The entity is changed first and checked afterwards; if a check fails the
     * exception rolls the whole transaction back, so a rejected edit leaves nothing behind.
     */
    @Transactional
    public SessionResponse update(TenantId tenantId, UUID eventId, UUID sessionId, SessionRequest req) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        EventSession s = find(eventId, sessionId);

        s.update(req.title(), req.description(), req.sessionType(), req.room(), req.speaker(),
                req.startAt(), req.endAt(), req.notes());
        assertWithinEvent(event, s);
        assertNoConflicts(eventId, s);

        sessionRepo.save(s);
        audit.record(tenantId, eventId, "SESSION_UPDATED", "SESSION", s.getId(),
                "Session updated: " + s.getTitle());
        return toResponse(s);
    }

    /** Only a session that never ran can be deleted; one that started or finished is part of the record. */
    @Transactional
    public void delete(TenantId tenantId, UUID eventId, UUID sessionId) {
        lockEvent(tenantId, eventId);
        EventSession s = find(eventId, sessionId);
        if (!EventSession.SCHEDULED.equals(s.getStatus()) && !EventSession.CANCELLED.equals(s.getStatus())) {
            throw new HandyFlowException("A " + s.getStatus() + " session is part of the record and can't be deleted",
                    HttpStatus.CONFLICT, "SESSION_HAS_RUN");
        }
        sessionRepo.delete(s);
        audit.record(tenantId, eventId, "SESSION_DELETED", "SESSION", sessionId,
                "Session deleted: " + s.getTitle());
    }

    // ── Running the day ───────────────────────────────────────────────────────

    @Transactional
    public SessionResponse start(TenantId tenantId, UUID eventId, UUID sessionId) {
        Event event = lockEvent(tenantId, eventId);
        if (!"LIVE".equals(event.getStatus())) {
            throw new HandyFlowException(
                    "Sessions can only be started while the event is LIVE (currently " + event.getStatus() + ")",
                    HttpStatus.CONFLICT, "EVENT_NOT_LIVE");
        }
        EventSession s = find(eventId, sessionId);
        if (s.getRoom() != null && sessionRepo.existsInProgressInRoom(eventId, s.getRoom(), s.getId())) {
            throw new HandyFlowException(
                    "Another session is still running in " + s.getRoom() + " — complete it first",
                    HttpStatus.CONFLICT, "ROOM_BUSY");
        }
        s.start(LocalDateTime.now(SAST));
        sessionRepo.save(s);

        Long delay = s.delayMinutes();
        audit.record(tenantId, eventId, "SESSION_STARTED", "SESSION", s.getId(),
                "Session started: " + s.getTitle()
                        + (delay != null && delay != 0 ? " (" + describeDelay(delay) + ")" : " (on time)"));
        return toResponse(s);
    }

    @Transactional
    public SessionResponse complete(TenantId tenantId, UUID eventId, UUID sessionId) {
        lockEvent(tenantId, eventId);
        EventSession s = find(eventId, sessionId);
        s.complete(LocalDateTime.now(SAST));
        sessionRepo.save(s);
        audit.record(tenantId, eventId, "SESSION_COMPLETED", "SESSION", s.getId(),
                "Session completed: " + s.getTitle());
        return toResponse(s);
    }

    @Transactional
    public SessionResponse cancel(TenantId tenantId, UUID eventId, UUID sessionId) {
        lockEvent(tenantId, eventId);
        EventSession s = find(eventId, sessionId);
        s.cancel();
        sessionRepo.save(s);
        audit.record(tenantId, eventId, "SESSION_CANCELLED", "SESSION", s.getId(),
                "Session cancelled: " + s.getTitle());
        return toResponse(s);
    }

    /**
     * "We're running 20 minutes late." Moves every SCHEDULED session from the anchor onward by the same
     * amount. Because they all move together their order and spacing are preserved; what could newly
     * clash is a moved session against one that ISN'T moving (one already running, or earlier), so the
     * result is checked and the whole shift is refused if it would double-book a room or a speaker.
     */
    @Transactional
    public List<SessionResponse> shift(TenantId tenantId, UUID eventId, ShiftSessionsRequest req) {
        Event event = lockEvent(tenantId, eventId);
        assertPlanningOpen(event);
        int minutes = req.minutes();
        if (minutes == 0) {
            throw new HandyFlowException("Shifting by 0 minutes changes nothing",
                    HttpStatus.BAD_REQUEST, "NO_SHIFT");
        }

        LocalDateTime anchor = req.fromSessionId() != null
                ? find(eventId, req.fromSessionId()).getStartAt()
                : LocalDateTime.now(SAST);

        List<EventSession> all = sessionRepo.findByEvent(eventId);
        List<EventSession> targets = all.stream()
                .filter(s -> EventSession.SCHEDULED.equals(s.getStatus()) && !s.getStartAt().isBefore(anchor))
                .toList();
        if (targets.isEmpty()) return List.of();

        targets.forEach(s -> s.shiftBy(minutes));

        List<Slot> slots = all.stream().filter(EventSession::isActive).map(this::slot).toList();
        Set<String> clashes = new LinkedHashSet<>();
        for (EventSession moved : targets) {
            clashes.addAll(SessionLogic.conflicts(slot(moved), slots));
        }
        if (!clashes.isEmpty()) {
            throw new HandyFlowException("Shifting by " + minutes + " minutes would clash: " + String.join("; ", clashes),
                    HttpStatus.CONFLICT, "SESSION_CONFLICT");
        }

        sessionRepo.saveAll(targets);
        audit.record(tenantId, eventId, "SESSIONS_SHIFTED", "SESSION", null,
                targets.size() + " session(s) shifted " + describeShift(minutes));
        return targets.stream().map(this::toResponse).toList();
    }

    // ── Checks ────────────────────────────────────────────────────────────────

    private void assertPlanningOpen(Event event) {
        if ("COMPLETED".equals(event.getStatus()) || "CANCELLED".equals(event.getStatus())) {
            throw new HandyFlowException("The programme can't be changed for a " + event.getStatus() + " event",
                    HttpStatus.CONFLICT, "EVENT_CLOSED");
        }
    }

    /** Setup and breakdown may sit outside the event; everything else must fall within it. */
    private void assertWithinEvent(Event event, EventSession s) {
        if (EventSession.MAY_FALL_OUTSIDE_EVENT.contains(s.getSessionType())) return;
        if (!SessionLogic.withinWindow(s.getStartAt(), s.getEndAt(), event.getStartDatetime(), event.getEndDatetime())) {
            throw new HandyFlowException(
                    "A " + s.getSessionType() + " session must fall within the event (" + event.getStartDatetime()
                            + " to " + event.getEndDatetime() + "); only SETUP and BREAKDOWN may be outside it",
                    HttpStatus.CONFLICT, "OUTSIDE_EVENT_WINDOW");
        }
    }

    private void assertNoConflicts(UUID eventId, EventSession candidate) {
        List<Slot> others = sessionRepo.findByEvent(eventId).stream()
                .filter(EventSession::isActive).map(this::slot).toList();
        List<String> conflicts = SessionLogic.conflicts(slot(candidate), others);
        if (!conflicts.isEmpty()) {
            throw new HandyFlowException(String.join("; ", conflicts), HttpStatus.CONFLICT, "SESSION_CONFLICT");
        }
    }

    private Slot slot(EventSession s) {
        return new Slot(s.getId(), s.getTitle(), s.getRoom(), s.getSpeaker(), s.getStartAt(), s.getEndAt());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Event findEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private Event lockEvent(TenantId tenantId, UUID eventId) {
        return eventRepo.findActiveByIdForUpdate(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
    }

    private EventSession find(UUID eventId, UUID sessionId) {
        return sessionRepo.findByIdAndEvent(sessionId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId.toString()));
    }

    private static String describeDelay(long minutes) {
        return minutes > 0 ? minutes + " min late" : (-minutes) + " min early";
    }

    private static String describeShift(int minutes) {
        return minutes > 0 ? minutes + " minutes later" : (-minutes) + " minutes earlier";
    }

    private SessionResponse toResponse(EventSession s) {
        return new SessionResponse(s.getId(), s.getTitle(), s.getDescription(), s.getSessionType(), s.getRoom(),
                s.getSpeaker(), s.getStartAt(), s.getEndAt(), s.getStatus(), s.getActualStart(), s.getActualEnd(),
                s.delayMinutes(), s.getNotes());
    }
}
