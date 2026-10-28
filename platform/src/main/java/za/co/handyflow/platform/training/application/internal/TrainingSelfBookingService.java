package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingBookingRules;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingRequirement;
import za.co.handyflow.platform.training.domain.model.TrainingRequirementRules;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingRequirementRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.OpenSession;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An employee booking themselves onto a session an administrator has opened for it, and taking themselves off. The person is always the
 * one linked to the signed-in login; no employee id is taken from the request. The booking itself goes through the same locked, checked
 * step that an administrator's booking does.
 */
@Service
@RequiredArgsConstructor
public class TrainingSelfBookingService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final HrFacade hrFacade;
    private final TrainingSessionRepository sessionRepository;
    private final TrainingCourseRepository courseRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingRequirementRepository requirementRepository;
    private final TrainingEnrollmentService enrollmentService;
    private final TrainingNotifier notifier;

    /** Sessions the caller could book now, soonest first. Ones they are already booked on, that are full, or that have begun are left out. */
    @Transactional(readOnly = true)
    public List<OpenSession> open(TenantId tenant, UUID userId) {
        EmployeeSummary me = me(tenant, userId);
        LocalDate today = LocalDate.now(SAST);
        Set<UUID> mine = enrollmentRepository.findAllForEmployee(tenant, me.id()).stream()
                .filter(e -> !"CANCELLED".equals(e.getStatus())).map(TrainingEnrollment::getSessionId).collect(Collectors.toSet());
        List<TrainingSession> candidates = sessionRepository.findAll(tenant, null, "SCHEDULED", org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .filter(TrainingSession::isOpenBooking).filter(s -> !s.getStartDate().isBefore(today)).toList();
        Map<UUID, TrainingCourse> courses = courseRepository.findAllById(candidates.stream().map(TrainingSession::getCourseId).distinct().toList()).stream()
                .filter(c -> tenant.getValue().equals(c.getTenantId()) && c.isActive()).collect(Collectors.toMap(TrainingCourse::getId, c -> c));
        List<TrainingRequirement> requirements = requirementRepository.findAllForTenant(tenant);

        return candidates.stream().filter(s -> courses.containsKey(s.getCourseId())).sorted(java.util.Comparator.comparing(TrainingSession::getStartDate)).map(s -> {
            long live = enrollmentRepository.countLiveBySession(tenant, s.getId());
            String problem = TrainingBookingRules.bookProblem(me.status(), s.getStatus(), s.isOpenBooking(), s.getStartDate(), today, mine.contains(s.getId()), s.getCapacity(), live);
            if (problem != null) return null;
            boolean required = requirements.stream().anyMatch(r -> s.getCourseId().equals(r.getCourseId())
                    && TrainingRequirementRules.applies(r.getJobTitle(), r.getDepartment(), me.jobTitle(), me.department()));
            return new OpenSession(s.getId(), s.getCourseId(), courses.get(s.getCourseId()).getTitle(), s.getStartDate(), s.getEndDate(), s.getVenue(),
                    s.getCapacity() == null ? null : (int) (s.getCapacity() - live), required);
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public TrainingEnrollment book(TenantId tenant, UUID userId, UUID sessionId) {
        EmployeeSummary me = me(tenant, userId);
        TrainingSession session = sessionRepository.findByTenantAndId(tenant, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSession", sessionId.toString()));
        boolean already = enrollmentRepository.findActiveEnrollment(tenant, sessionId, me.id()).isPresent();
        String problem = TrainingBookingRules.bookProblem(me.status(), session.getStatus(), session.isOpenBooking(), session.getStartDate(),
                LocalDate.now(SAST), already, session.getCapacity(), enrollmentRepository.countLiveBySession(tenant, sessionId));
        if (problem != null) throw new IllegalStateException(problem);
        // The locked step re-checks the places left, so two people taking the last one cannot both succeed.
        TrainingEnrollment saved = enrollmentService.enroll(tenant, sessionId, me.id(), "Booked by the employee");
        notifier.tellManager(tenant, me, session, true);
        return saved;
    }

    @Transactional
    public TrainingEnrollment cancel(TenantId tenant, UUID userId, UUID enrollmentId) {
        EmployeeSummary me = me(tenant, userId);
        // Someone else's booking looks the same as one that does not exist.
        TrainingEnrollment enrollment = enrollmentRepository.findByTenantAndId(tenant, enrollmentId).filter(e -> me.id().equals(e.getEmployeeId()))
                .orElseThrow(() -> new ResourceNotFoundException("TrainingEnrollment", enrollmentId.toString()));
        TrainingSession session = sessionRepository.findByTenantAndId(tenant, enrollment.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSession", enrollment.getSessionId().toString()));
        String problem = TrainingBookingRules.cancelProblem(enrollment.getStatus(), session.getStatus(), session.getStartDate(), LocalDate.now(SAST));
        if (problem != null) throw new IllegalStateException(problem);
        enrollment.cancel("Cancelled by the employee");
        TrainingEnrollment saved = enrollmentRepository.save(enrollment);
        notifier.tellManager(tenant, me, session, false);
        return saved;
    }

    private EmployeeSummary me(TenantId tenant, UUID userId) {
        return hrFacade.findEmployeeByUserId(tenant, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee record for your login", String.valueOf(userId)));
    }
}
