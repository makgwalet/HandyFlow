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
import za.co.handyflow.platform.trainingprovider.domain.rules.AttendanceRules;
import za.co.handyflow.platform.trainingprovider.dto.AttendanceDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

/**
 * The attendance register for a timetable slot, and per-learner counts for a cohort. Attendance is recorded on or after the day, once per
 * learner per slot. A change is a correction: it needs a written reason and leaves a history line. Nothing is deleted. Attendance never
 * decides competence or completion.
 */
@Service
@RequiredArgsConstructor
public class TrainProvAttendanceService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final String NOTE = "Counts of what was recorded. The rate leaves out excused and unrecorded sessions. It is not a result and does not say a learner is competent.";

    private final TrainProvAttendanceRepository attendance;
    private final TrainProvAttendanceEventRepository history;
    private final TrainProvTimetableSlotRepository slots;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvCohortMemberRepository members;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvProgrammeRepository programmes;

    public Register register(TenantId t, UUID slotId, LocalDate today) {
        TrainProvTimetableSlot s = slot(t, slotId);
        TrainProvCohort c = cohort(t, s.getCohortId());
        Map<UUID, TrainProvAttendance> byMember = new HashMap<>();
        for (TrainProvAttendance a : attendance.findForSlot(t, slotId)) {
            byMember.put(a.getMemberId(), a);
        }
        List<RegisterRow> rows = new ArrayList<>();
        for (TrainProvCohortMember m : members.findForCohort(t, c.getId())) {
            TrainProvAttendance a = byMember.get(m.getId());
            if (!"ACTIVE".equals(m.getStatus()) && a == null) {
                continue;
            }
            Who who = who(t, m);
            int corrections = a == null ? 0 : (int) history.findForAttendance(t, a.getId()).stream().filter(e -> "CORRECTED".equals(e.getAction())).count();
            rows.add(new RegisterRow(m.getId(), m.getEnrolmentId(), who.enrolmentNo, who.learnerId, who.name, a == null ? null : a.getId(),
                    a == null ? null : a.getStatus(), a == null ? null : a.getNote(), corrections));
        }
        String why = !"SCHEDULED".equals(s.getStatus()) ? "This slot is cancelled"
                : s.getSlotDate().isAfter(today) ? "This slot is on " + s.getSlotDate() + ". Attendance can be recorded on or after the day" : null;
        return new Register(s.getId(), c.getId(), c.getName(), s.getTitle(), s.getSlotDate(), s.getStartTime(), s.getEndTime(), s.getStatus(), why == null, why, rows);
    }

    @Transactional
    public Register record(TenantId t, UUID slotId, RecordRequest r, UUID by, LocalDate today) {
        TrainProvTimetableSlot s = slot(t, slotId);
        Set<UUID> seen = new HashSet<>();
        List<TrainProvAttendance> toSave = new ArrayList<>();
        for (Entry e : r.entries()) {
            if (!seen.add(e.memberId())) {
                throw new HandyFlowException("A learner appears twice in the register", HttpStatus.BAD_REQUEST, "TP_ATTENDANCE_INVALID");
            }
            TrainProvCohortMember m = members.findForTenant(t, e.memberId())
                    .orElseThrow(() -> new ResourceNotFoundException("TrainProvCohortMember", e.memberId().toString()));
            if (!m.getCohortId().equals(s.getCohortId())) {
                throw new HandyFlowException("That learner is not in this slot's cohort", HttpStatus.CONFLICT, "TP_ATTENDANCE_STATE");
            }
            if (attendance.findForSlotAndMember(t, slotId, m.getId()).isPresent()) {
                throw new HandyFlowException("Attendance is already recorded for " + who(t, m).name + ". Correct it instead", HttpStatus.CONFLICT, "TP_ATTENDANCE_STATE");
            }
            String problem = AttendanceRules.recordProblem(s.getStatus(), s.getSlotDate(), today, m.getStatus(), e.status(), e.note());
            if (problem != null) {
                throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_ATTENDANCE_STATE");
            }
            toSave.add(TrainProvAttendance.create(t, slotId, m.getId(), e.status(), e.note(), by));
        }
        for (TrainProvAttendance a : toSave) {
            attendance.save(a);
            history.save(TrainProvAttendanceEvent.of(t, a.getId(), "RECORDED", null, a.getStatus(), null, by));
        }
        return register(t, slotId, today);
    }

    @Transactional
    public Register correct(TenantId t, UUID attendanceId, CorrectRequest r, UUID by, LocalDate today) {
        TrainProvAttendance a = attendance.findForTenant(t, attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvAttendance", attendanceId.toString()));
        String from = a.getStatus();
        String problem = AttendanceRules.correctionProblem(from, r.status(), a.getNote(), r.note(), r.reason());
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_ATTENDANCE_STATE");
        }
        a.correct(r.status(), r.note());
        attendance.save(a);
        history.save(TrainProvAttendanceEvent.of(t, a.getId(), "CORRECTED", from, a.getStatus(), r.reason(), by));
        return register(t, a.getSlotId(), today);
    }

    public List<HistoryLine> historyOf(TenantId t, UUID attendanceId) {
        attendance.findForTenant(t, attendanceId).orElseThrow(() -> new ResourceNotFoundException("TrainProvAttendance", attendanceId.toString()));
        return history.findForAttendance(t, attendanceId).stream()
                .<HistoryLine>map(e -> new HistoryLine(e.getId(), e.getAction(), e.getFromStatus(), e.getToStatus(), e.getReason(), e.getActedBy(), e.getActedAt())).toList();
    }

    public CohortSummary summary(TenantId t, UUID cohortId, LocalDate today) {
        TrainProvCohort c = cohort(t, cohortId);
        List<TrainProvTimetableSlot> held = heldSlots(t, cohortId, today);
        List<TrainProvAttendance> all = attendance.findForCohort(t, cohortId);
        List<SummaryRow> rows = new ArrayList<>();
        for (TrainProvCohortMember m : members.findForCohort(t, cohortId)) {
            if (!"ACTIVE".equals(m.getStatus())) {
                continue;
            }
            AttendanceRules.Summary sum = counts(m, held, all);
            Who who = who(t, m);
            rows.add(new SummaryRow(m.getId(), m.getEnrolmentId(), who.enrolmentNo, who.learnerId, who.name, sum.expected(), sum.attended(), sum.absent(),
                    sum.excused(), sum.unrecorded(), sum.ratePercent()));
        }
        return new CohortSummary(c.getId(), NOTE, rows);
    }

    /** Every cohort a learner has been in, current and past, with counts of what was recorded. Counts only: no result, no completion. */
    public LearnerCohorts forLearner(TenantId t, UUID learnerId, LocalDate today) {
        learners.findForTenant(t, learnerId).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearner", learnerId.toString()));
        List<LearnerCohortRow> rows = new ArrayList<>();
        for (TrainProvCohortMember m : members.findForLearner(t, learnerId)) {
            TrainProvCohort c = cohort(t, m.getCohortId());
            AttendanceRules.Summary sum = counts(m, heldSlots(t, c.getId(), today), attendance.findForCohort(t, c.getId()));
            String enrolmentNo = enrolments.findForTenant(t, m.getEnrolmentId()).map(TrainProvLearnerEnrolment::getEnrolmentNo).orElse("");
            String title = programmes.findForTenant(t, c.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse("");
            rows.add(new LearnerCohortRow(m.getId(), c.getId(), c.getCohortNo(), c.getName(), title, c.getStatus(), m.getStatus(), m.getRemovedReason(), enrolmentNo,
                    c.getStartDate(), c.getEndDate(), sum.expected(), sum.attended(), sum.absent(), sum.excused(), sum.unrecorded(), sum.ratePercent()));
        }
        return new LearnerCohorts(NOTE, rows);
    }

    private List<TrainProvTimetableSlot> heldSlots(TenantId t, UUID cohortId, LocalDate today) {
        return slots.findForCohort(t, cohortId).stream().filter(s -> "SCHEDULED".equals(s.getStatus()) && !s.getSlotDate().isAfter(today)).toList();
    }

    /** Slots held while the learner was in the cohort: from the day they joined, and up to the day they were removed if they were. */
    private AttendanceRules.Summary counts(TrainProvCohortMember m, List<TrainProvTimetableSlot> held, List<TrainProvAttendance> all) {
        LocalDate joined = m.getAddedAt().atZone(SAST).toLocalDate();
        LocalDate left = m.getRemovedAt() == null ? null : m.getRemovedAt().atZone(SAST).toLocalDate();
        Set<UUID> expectedSlots = new HashSet<>();
        for (TrainProvTimetableSlot s : held) {
            if (!s.getSlotDate().isBefore(joined) && (left == null || !s.getSlotDate().isAfter(left))) {
                expectedSlots.add(s.getId());
            }
        }
        int attended = 0;
        int absent = 0;
        int excused = 0;
        for (TrainProvAttendance a : all) {
            if (a.getMemberId().equals(m.getId()) && expectedSlots.contains(a.getSlotId())) {
                if (AttendanceRules.attended(a.getStatus())) {
                    attended++;
                } else if ("ABSENT".equals(a.getStatus())) {
                    absent++;
                } else {
                    excused++;
                }
            }
        }
        return AttendanceRules.summary(expectedSlots.size(), attended, absent, excused);
    }

    // ---- lookups ----

    private record Who(String enrolmentNo, UUID learnerId, String name) {
    }

    private Who who(TenantId t, TrainProvCohortMember m) {
        TrainProvLearnerEnrolment e = enrolments.findForTenant(t, m.getEnrolmentId()).orElse(null);
        if (e == null) {
            return new Who("", null, "");
        }
        String name = learners.findForTenant(t, e.getLearnerId()).map(l -> l.getFirstNames() + " " + l.getSurname()).orElse("");
        return new Who(e.getEnrolmentNo(), e.getLearnerId(), name);
    }

    private TrainProvTimetableSlot slot(TenantId t, UUID id) {
        return slots.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvTimetableSlot", id.toString()));
    }

    private TrainProvCohort cohort(TenantId t, UUID id) {
        return cohorts.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohort", id.toString()));
    }
}
