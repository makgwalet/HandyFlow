package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.PractitionerRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.TimetableRules;
import za.co.handyflow.platform.trainingprovider.dto.TimetableDtos.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The timetable of a cohort. A slot may name a venue, a facilitator and a programme module. The venue and the facilitator cannot be in two
 * places at once, the cohort cannot be in two places at once, and the facilitator must be assigned to the cohort and have an appointment
 * that is checked and in force on the slot's own date. Slots are cancelled with a reason, never deleted.
 *
 * Clashes are found on real moments in time (a start and end with a time zone), for the venue, the facilitator, the cohort and every learner
 * in the cohort. A clash is refused unless an administrator approves it with a written reason; each approved clash is kept as an override
 * record. Before checking, the request waits for any other booking in progress for the same venue, person, cohort or learner, so two
 * requests cannot both pass. The table itself is not yet protected by a database exclusion constraint.
 */
@Service
@RequiredArgsConstructor
public class TrainProvTimetableService {

    private final TrainProvTimetableSlotRepository slots;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvCohortMemberRepository members;
    private final TrainProvCohortFacilitatorRepository facilitators;
    private final TrainProvCohortEventRepository events;
    private final TrainProvVenueRepository venues;
    private final TrainProvProgrammeModuleRepository modules;
    private final TrainProvPractitionerRepository practitioners;
    private final TrainProvPractitionerAppointmentRepository appointments;
    private final TrainProvTimetableOverrideRepository overrides;
    private final TrainProvLearnerRepository learners;

    public List<SlotView> forCohort(TenantId t, UUID cohortId) {
        TrainProvCohort c = cohort(t, cohortId);
        return slots.findForCohort(t, cohortId).stream().<SlotView>map(s -> view(t, s, c.getName())).toList();
    }

    public List<SlotView> window(TenantId t, LocalDate from, LocalDate to, UUID venueId, UUID practitionerId, UUID cohortId) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new HandyFlowException("Give a from date and a to date that is not before it", HttpStatus.BAD_REQUEST, "TP_TIMETABLE_INVALID");
        }
        if (to.isAfter(from.plusDays(366))) {
            throw new HandyFlowException("Ask for no more than a year at a time", HttpStatus.BAD_REQUEST, "TP_TIMETABLE_INVALID");
        }
        return slots.window(t, from, to, venueId, practitionerId, cohortId).stream().<SlotView>map(s -> view(t, s, null)).toList();
    }

    public SlotView add(TenantId t, UUID cohortId, SlotRequest r, UUID by) {
        return add(t, cohortId, r, by, false);
    }

    /** mayApprove is true only for a caller with administrator authority; it is what lets overrideReason book over a clash. */
    @Transactional
    public SlotView add(TenantId t, UUID cohortId, SlotRequest r, UUID by, boolean mayApprove) {
        TrainProvCohort c = cohort(t, cohortId);
        Plan plan = plan(t, c, r, null, TimetableRules.DEFAULT_ZONE);
        TrainProvTimetableSlot s = guard(() -> TrainProvTimetableSlot.create(t, cohortId, r.title(), r.slotDate(), r.startTime(), r.endTime(),
                r.moduleId(), r.venueId(), r.practitionerId(), plan.zone(), by));
        slots.save(s);
        recordOverrides(t, c, s, plan, r.overrideReason(), by, mayApprove);
        events.save(TrainProvCohortEvent.of(t, cohortId, "SLOT_ADDED", describe(s), by));
        return view(t, s, c.getName());
    }

    public SlotView change(TenantId t, UUID slotId, SlotRequest r, UUID by) {
        return change(t, slotId, r, by, false);
    }

    @Transactional
    public SlotView change(TenantId t, UUID slotId, SlotRequest r, UUID by, boolean mayApprove) {
        TrainProvTimetableSlot s = slot(t, slotId);
        if (!"SCHEDULED".equals(s.getStatus())) {
            throw new HandyFlowException("A cancelled slot cannot be changed", HttpStatus.CONFLICT, "TP_TIMETABLE_STATE");
        }
        TrainProvCohort c = cohort(t, s.getCohortId());
        Plan plan = plan(t, c, r, s.getId(), s.getTimeZone());
        guard(() -> { s.change(r.title(), r.slotDate(), r.startTime(), r.endTime(), r.moduleId(), r.venueId(), r.practitionerId(), plan.zone()); return null; });
        slots.save(s);
        recordOverrides(t, c, s, plan, r.overrideReason(), by, mayApprove);
        events.save(TrainProvCohortEvent.of(t, c.getId(), "SLOT_CHANGED", describe(s), by));
        return view(t, s, c.getName());
    }

    /** What would clash if this slot were booked, without booking it. slotId, when given, is the slot being changed. */
    @Transactional
    public ClashCheck check(TenantId t, UUID cohortId, SlotRequest r, UUID slotId) {
        TrainProvCohort c = cohort(t, cohortId);
        String zone = slotId == null ? TimetableRules.DEFAULT_ZONE : slot(t, slotId).getTimeZone();
        Plan plan = plan(t, c, r, slotId, zone);
        List<ClashView> views = plan.found().stream().map(Found::clash)
                .map(k -> new ClashView(k.resourceType(), k.resourceId(), k.resourceLabel(), k.otherSlotId(), k.otherCohortId(), k.message())).toList();
        return new ClashCheck(views.isEmpty(), views);
    }

    public List<OverrideView> overridesFor(TenantId t, UUID slotId) {
        slot(t, slotId);
        return overrides.findForSlot(t, slotId).stream().map(o -> new OverrideView(o.getId(), o.getSlotId(), o.getResourceType(), o.getResourceId(),
                o.getResourceLabel(), o.getClashingSlotId(), o.getSlotStartsAt(), o.getSlotEndsAt(), o.getClashStartsAt(), o.getClashEndsAt(),
                o.getReason(), o.getApprovedBy(), o.getApprovedAt())).toList();
    }

    @Transactional
    public SlotView cancel(TenantId t, UUID slotId, String reason, UUID by) {
        TrainProvTimetableSlot s = slot(t, slotId);
        TrainProvCohort c = cohort(t, s.getCohortId());
        guard(() -> { s.cancel(reason, by); return null; });
        slots.save(s);
        events.save(TrainProvCohortEvent.of(t, c.getId(), "SLOT_CANCELLED", describe(s) + ": " + reason.trim(), by));
        return view(t, s, c.getName());
    }

    // ---- checks ----

    private record Found(TimetableRules.Clash clash, TrainProvTimetableSlot other) {
    }

    /** The moments the slot would occupy, the zone it was read in, and every clash it would cause. Throws for anything that is not a clash. */
    private record Plan(String zone, Instant from, Instant to, List<Found> found) {
    }

    private Plan plan(TenantId t, TrainProvCohort c, SlotRequest r, UUID selfId, String defaultZone) {
        refuse(TimetableRules.slotProblem(r.title(), r.slotDate(), r.startTime(), r.endTime(), c.getStatus(), c.getStartDate(), c.getEndDate()));
        String zone = r.timeZone() == null || r.timeZone().isBlank() ? defaultZone : r.timeZone().trim();
        String zoneProblem = TimetableRules.zoneProblem(zone);
        if (zoneProblem != null) {
            throw new HandyFlowException(zoneProblem, HttpStatus.BAD_REQUEST, "TP_TIMETABLE_INVALID");
        }
        Instant from = TimetableRules.instant(r.slotDate(), r.startTime(), zone);
        Instant to = TimetableRules.instant(r.slotDate(), r.endTime(), zone);
        if (!to.isAfter(from)) {
            throw new HandyFlowException("The end time must be after the start time", HttpStatus.BAD_REQUEST, "TP_TIMETABLE_INVALID");
        }
        if (r.moduleId() != null) {
            TrainProvProgrammeModule m = modules.findForTenant(t, r.moduleId())
                    .orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeModule", r.moduleId().toString()));
            if (!m.getProgrammeVersionId().equals(c.getProgrammeVersionId())) {
                throw new HandyFlowException("That module belongs to a different version of the programme", HttpStatus.CONFLICT, "TP_TIMETABLE_STATE");
            }
        }
        TrainProvVenue venue = null;
        if (r.venueId() != null) {
            venue = venues.findForTenant(t, r.venueId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvVenue", r.venueId().toString()));
            refuse(TimetableRules.venueProblem("ACTIVE".equals(venue.getStatus()), venue.getSiteId(), c.getSiteId(), venue.getCapacity(), members.countActive(t, c.getId())));
        }
        TrainProvPractitioner person = null;
        if (r.practitionerId() != null) {
            TrainProvCohortFacilitator f = facilitators.findLive(t, c.getId(), r.practitionerId()).orElse(null);
            PractitionerRules.Standing standing = f == null ? PractitionerRules.Standing.NOT_VERIFIED
                    : appointments.findForTenant(t, f.getAppointmentId()).map(a -> a.standingOn(r.slotDate())).orElse(PractitionerRules.Standing.NOT_VERIFIED);
            refuse(TimetableRules.facilitatorProblem(f != null, standing, r.slotDate()));
            person = practitioners.findForTenant(t, r.practitionerId()).orElse(null);
        }
        List<UUID> learnerIds = slots.activeLearnerIds(t, c.getId());
        lockAll(t, c.getId(), r.venueId(), r.practitionerId(), learnerIds);

        List<Found> found = new ArrayList<>();
        if (venue != null) {
            for (TrainProvTimetableSlot other : slots.liveForVenueBetween(t, venue.getId(), from, to)) {
                if (!other.getId().equals(selfId)) {
                    found.add(new Found(new TimetableRules.Clash(TimetableRules.VENUE, venue.getId(), venue.getName(), other.getId(), other.getCohortId(),
                            venue.getName() + " is already booked from " + other.getStartTime() + " to " + other.getEndTime() + " on " + other.getSlotDate()), other));
                }
            }
        }
        if (r.practitionerId() != null) {
            String who = person == null ? "Facilitator" : person.fullName();
            for (TrainProvTimetableSlot other : slots.liveForPractitionerBetween(t, r.practitionerId(), from, to)) {
                if (!other.getId().equals(selfId)) {
                    found.add(new Found(new TimetableRules.Clash(TimetableRules.PRACTITIONER, r.practitionerId(), who, other.getId(), other.getCohortId(),
                            "That facilitator is already teaching from " + other.getStartTime() + " to " + other.getEndTime() + " on " + other.getSlotDate()), other));
                }
            }
        }
        for (TrainProvTimetableSlot other : slots.liveForCohortBetween(t, c.getId(), from, to)) {
            if (!other.getId().equals(selfId)) {
                found.add(new Found(new TimetableRules.Clash(TimetableRules.COHORT, c.getId(), c.getName(), other.getId(), other.getCohortId(),
                        "The cohort already has \"" + other.getTitle() + "\" from " + other.getStartTime() + " to " + other.getEndTime() + " that day"), other));
            }
        }
        if (!learnerIds.isEmpty()) {
            for (Object[] row : slots.otherSlotsOfLearners(t, c.getId(), learnerIds, from, to)) {
                TrainProvTimetableSlot other = (TrainProvTimetableSlot) row[0];
                UUID learnerId = (UUID) row[1];
                String name = learners.findForTenant(t, learnerId).map(l -> l.getFirstNames() + " " + l.getSurname()).orElse("A learner");
                String otherCohort = cohorts.findForTenant(t, other.getCohortId()).map(TrainProvCohort::getName).orElse("another cohort");
                found.add(new Found(new TimetableRules.Clash(TimetableRules.LEARNER, learnerId, name, other.getId(), other.getCohortId(),
                        name + " is also in " + otherCohort + ", which has \"" + other.getTitle() + "\" from " + other.getStartTime() + " to "
                                + other.getEndTime() + " on " + other.getSlotDate()), other));
            }
        }
        return new Plan(zone, from, to, found);
    }

    /** Locks in a fixed order so two bookings that share several things cannot wait on each other. */
    private void lockAll(TenantId t, UUID cohortId, UUID venueId, UUID practitionerId, List<UUID> learnerIds) {
        TreeSet<String> keys = new TreeSet<>();
        String prefix = "trainprov-timetable:" + t.getValue() + ":";
        keys.add(prefix + "C:" + cohortId);
        if (venueId != null) {
            keys.add(prefix + "V:" + venueId);
        }
        if (practitionerId != null) {
            keys.add(prefix + "P:" + practitionerId);
        }
        learnerIds.forEach(id -> keys.add(prefix + "L:" + id));
        keys.forEach(slots::lock);
    }

    /** Refuses unless there are no clashes or an administrator approved them with a reason; then keeps one record per clash. */
    private void recordOverrides(TenantId t, TrainProvCohort c, TrainProvTimetableSlot s, Plan plan, String reason, UUID by, boolean mayApprove) {
        if (plan.found().isEmpty()) {
            return;
        }
        List<TimetableRules.Clash> clashes = plan.found().stream().map(Found::clash).toList();
        String problem = TimetableRules.overrideProblem(clashes, reason, mayApprove);
        if (problem != null) {
            boolean denied = TimetableRules.reasonOk(reason) && !mayApprove;
            throw new HandyFlowException(problem, denied ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT,
                    denied ? "TP_TIMETABLE_OVERRIDE_DENIED" : "TP_TIMETABLE_CLASH");
        }
        for (Found f : plan.found()) {
            overrides.save(guard(() -> TrainProvTimetableOverride.of(t, s, f.clash(), f.other(), reason, by)));
        }
        events.save(TrainProvCohortEvent.of(t, c.getId(), "SLOT_OVERRIDE", describe(s) + " booked over " + plan.found().size() + " clash(es) with approval: "
                + reason.trim(), by));
    }

    private static void refuse(String problem) {
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_TIMETABLE_STATE");
        }
    }

    // ---- views and lookups ----

    private SlotView view(TenantId t, TrainProvTimetableSlot s, String cohortName) {
        String cName = cohortName != null ? cohortName : cohorts.findForTenant(t, s.getCohortId()).map(TrainProvCohort::getName).orElse("");
        String moduleTitle = s.getModuleId() == null ? null : modules.findForTenant(t, s.getModuleId()).map(TrainProvProgrammeModule::getTitle).orElse(null);
        String venueName = s.getVenueId() == null ? null : venues.findForTenant(t, s.getVenueId()).map(TrainProvVenue::getName).orElse(null);
        String person = null;
        String standingName = null;
        String standingNote = null;
        if (s.getPractitionerId() != null) {
            person = practitioners.findForTenant(t, s.getPractitionerId()).map(TrainProvPractitioner::fullName).orElse(null);
            PractitionerRules.Standing st = facilitators.findLive(t, s.getCohortId(), s.getPractitionerId())
                    .flatMap(f -> appointments.findForTenant(t, f.getAppointmentId())).map(a -> a.standingOn(s.getSlotDate())).orElse(PractitionerRules.Standing.NOT_VERIFIED);
            standingName = st.name();
            standingNote = st.note();
        }
        return new SlotView(s.getId(), s.getCohortId(), cName, s.getSlotDate(), s.getStartTime(), s.getEndTime(), s.getTitle(), s.getModuleId(), moduleTitle,
                s.getVenueId(), venueName, s.getPractitionerId(), person, standingName, standingNote, s.getStatus(), s.getCancelledReason(),
                s.getTimeZone(), s.getStartsAt(), s.getEndsAt(), overrides.countForSlot(t, s.getId()));
    }

    private static String describe(TrainProvTimetableSlot s) {
        return s.getTitle() + " on " + s.getSlotDate() + " " + s.getStartTime() + "-" + s.getEndTime();
    }

    private TrainProvCohort cohort(TenantId t, UUID id) {
        return cohorts.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohort", id.toString()));
    }

    private TrainProvTimetableSlot slot(TenantId t, UUID id) {
        return slots.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvTimetableSlot", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_TIMETABLE_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_TIMETABLE_INVALID");
        }
    }
}
