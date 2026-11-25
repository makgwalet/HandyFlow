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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The timetable of a cohort. A slot may name a venue, a facilitator and a programme module. The venue and the facilitator cannot be in two
 * places at once, the cohort cannot be in two places at once, and the facilitator must be assigned to the cohort and have an appointment
 * that is checked and in force on the slot's own date. Slots are cancelled with a reason, never deleted.
 *
 * The clash checks run in the service, so two requests at the same instant could both pass. The table is not yet protected against that.
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

    @Transactional
    public SlotView add(TenantId t, UUID cohortId, SlotRequest r, UUID by) {
        TrainProvCohort c = cohort(t, cohortId);
        check(t, c, r, null);
        TrainProvTimetableSlot s = guard(() -> TrainProvTimetableSlot.create(t, cohortId, r.title(), r.slotDate(), r.startTime(), r.endTime(),
                r.moduleId(), r.venueId(), r.practitionerId(), by));
        slots.save(s);
        events.save(TrainProvCohortEvent.of(t, cohortId, "SLOT_ADDED", describe(s), by));
        return view(t, s, c.getName());
    }

    @Transactional
    public SlotView change(TenantId t, UUID slotId, SlotRequest r, UUID by) {
        TrainProvTimetableSlot s = slot(t, slotId);
        if (!"SCHEDULED".equals(s.getStatus())) {
            throw new HandyFlowException("A cancelled slot cannot be changed", HttpStatus.CONFLICT, "TP_TIMETABLE_STATE");
        }
        TrainProvCohort c = cohort(t, s.getCohortId());
        check(t, c, r, s.getId());
        guard(() -> { s.change(r.title(), r.slotDate(), r.startTime(), r.endTime(), r.moduleId(), r.venueId(), r.practitionerId()); return null; });
        slots.save(s);
        events.save(TrainProvCohortEvent.of(t, c.getId(), "SLOT_CHANGED", describe(s), by));
        return view(t, s, c.getName());
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

    private void check(TenantId t, TrainProvCohort c, SlotRequest r, UUID selfId) {
        refuse(TimetableRules.slotProblem(r.title(), r.slotDate(), r.startTime(), r.endTime(), c.getStatus(), c.getStartDate(), c.getEndDate()));
        if (r.moduleId() != null) {
            TrainProvProgrammeModule m = modules.findForTenant(t, r.moduleId())
                    .orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeModule", r.moduleId().toString()));
            if (!m.getProgrammeVersionId().equals(c.getProgrammeVersionId())) {
                throw new HandyFlowException("That module belongs to a different version of the programme", HttpStatus.CONFLICT, "TP_TIMETABLE_STATE");
            }
        }
        if (r.venueId() != null) {
            TrainProvVenue v = venues.findForTenant(t, r.venueId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvVenue", r.venueId().toString()));
            refuse(TimetableRules.venueProblem("ACTIVE".equals(v.getStatus()), v.getSiteId(), c.getSiteId(), v.getCapacity(), members.countActive(t, c.getId())));
            for (TrainProvTimetableSlot other : slots.liveForVenueOn(t, v.getId(), r.slotDate())) {
                if (!other.getId().equals(selfId) && TimetableRules.overlaps(r.startTime(), r.endTime(), other.getStartTime(), other.getEndTime())) {
                    throw new HandyFlowException(v.getName() + " is already booked from " + other.getStartTime() + " to " + other.getEndTime() + " on " + r.slotDate(),
                            HttpStatus.CONFLICT, "TP_TIMETABLE_CLASH");
                }
            }
        }
        if (r.practitionerId() != null) {
            TrainProvCohortFacilitator f = facilitators.findLive(t, c.getId(), r.practitionerId()).orElse(null);
            PractitionerRules.Standing standing = f == null ? PractitionerRules.Standing.NOT_VERIFIED
                    : appointments.findForTenant(t, f.getAppointmentId()).map(a -> a.standingOn(r.slotDate())).orElse(PractitionerRules.Standing.NOT_VERIFIED);
            refuse(TimetableRules.facilitatorProblem(f != null, standing, r.slotDate()));
            for (TrainProvTimetableSlot other : slots.liveForPractitionerOn(t, r.practitionerId(), r.slotDate())) {
                if (!other.getId().equals(selfId) && TimetableRules.overlaps(r.startTime(), r.endTime(), other.getStartTime(), other.getEndTime())) {
                    throw new HandyFlowException("That facilitator is already teaching from " + other.getStartTime() + " to " + other.getEndTime() + " on " + r.slotDate(),
                            HttpStatus.CONFLICT, "TP_TIMETABLE_CLASH");
                }
            }
        }
        for (TrainProvTimetableSlot other : slots.findForCohort(t, c.getId())) {
            if (!other.getId().equals(selfId) && "SCHEDULED".equals(other.getStatus()) && other.getSlotDate().equals(r.slotDate())
                    && TimetableRules.overlaps(r.startTime(), r.endTime(), other.getStartTime(), other.getEndTime())) {
                throw new HandyFlowException("The cohort already has \"" + other.getTitle() + "\" from " + other.getStartTime() + " to " + other.getEndTime() + " that day",
                        HttpStatus.CONFLICT, "TP_TIMETABLE_CLASH");
            }
        }
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
                s.getVenueId(), venueName, s.getPractitionerId(), person, standingName, standingNote, s.getStatus(), s.getCancelledReason());
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
