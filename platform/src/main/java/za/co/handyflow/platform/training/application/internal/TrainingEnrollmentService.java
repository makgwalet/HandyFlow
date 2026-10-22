package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingRules;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * The centerpiece service — owns every enrollment-lifecycle mutation,
 * including the capacity check (a live count against {@code
 * TrainingEnrollmentRepository}, not a denormalized tally — see {@code
 * TrainingSession}'s own Javadoc) and the employee-existence check via
 * {@code HrFacade}, mirroring the exact confirmed real precedent
 * {@code RecruiterService} already established for reaching HR from a
 * sibling module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingEnrollmentService {

    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingSessionRepository sessionRepository;
    private final HrFacade hrFacade;
    private final TrainingNotifier notifier;
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    @Transactional
    public TrainingEnrollment enroll(TenantId tenantId, UUID sessionId, UUID employeeId, String notes) {
        // The session row is locked so two people enrolling at once cannot both take the last place.
        TrainingSession session = sessionRepository.findByTenantAndIdForUpdate(tenantId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSession", sessionId.toString()));

        EmployeeSummary employee = hrFacade.findEmployeeById(tenantId, employeeId)
                .orElseThrow(() -> new HandyFlowException(
                        "Employee " + employeeId + " was not found in HR", HttpStatus.BAD_REQUEST, "EMPLOYEE_NOT_FOUND"));
        String problem = TrainingRules.enrollProblem(employee.status(), session.getStatus());
        if (problem != null) throw new IllegalStateException(problem);

        enrollmentRepository.findActiveEnrollment(tenantId, sessionId, employeeId).ifPresent(existing -> {
            throw new IllegalStateException("Employee " + employeeId + " is already enrolled in this session");
        });

        long liveCount = enrollmentRepository.countLiveBySession(tenantId, sessionId);
        if (session.isFull(liveCount)) {
            throw new IllegalStateException("Session " + sessionId + " is at capacity (" + session.getCapacity() + ")");
        }

        TrainingEnrollment enrollment = TrainingEnrollment.create(tenantId, sessionId, employeeId,
                employee.fullName(), employee.employeeNumber(), notes);
        TrainingEnrollment saved = enrollmentRepository.save(enrollment);
        notifier.enrolled(tenantId, session, saved, employee);
        return saved;
    }

    @Transactional
    public TrainingEnrollment markAttended(TenantId tenantId, UUID id) {
        TrainingEnrollment enrollment = get(tenantId, id);
        requireAttendance(tenantId, enrollment);
        enrollment.markAttended();
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public TrainingEnrollment markNoShow(TenantId tenantId, UUID id) {
        TrainingEnrollment enrollment = get(tenantId, id);
        requireAttendance(tenantId, enrollment);
        enrollment.markNoShow();
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public TrainingEnrollment cancel(TenantId tenantId, UUID id, String reason) {
        TrainingEnrollment enrollment = get(tenantId, id);
        enrollment.cancel(reason);
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public TrainingEnrollment complete(TenantId tenantId, UUID id, BigDecimal score, boolean passed) {
        TrainingEnrollment enrollment = get(tenantId, id);
        TrainingSession session = sessionOf(tenantId, enrollment);
        String problem = TrainingRules.resultProblem(enrollment.getStatus(), session.getStatus(), session.getStartDate(), LocalDate.now(ZONE), score);
        if (problem != null) throw new IllegalStateException(problem);
        enrollment.complete(score, passed);
        return enrollmentRepository.save(enrollment);
    }

    private void requireAttendance(TenantId tenantId, TrainingEnrollment enrollment) {
        TrainingSession session = sessionOf(tenantId, enrollment);
        String problem = TrainingRules.attendanceProblem(enrollment.getStatus(), session.getStatus(), session.getStartDate(), LocalDate.now(ZONE));
        if (problem != null) throw new IllegalStateException(problem);
    }

    private TrainingSession sessionOf(TenantId tenantId, TrainingEnrollment enrollment) {
        return sessionRepository.findByTenantAndId(tenantId, enrollment.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSession", enrollment.getSessionId().toString()));
    }

    public TrainingEnrollment get(TenantId tenantId, UUID id) {
        return enrollmentRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingEnrollment", id.toString()));
    }

    public Page<TrainingEnrollment> list(TenantId tenantId, UUID sessionId, UUID employeeId, String status, Pageable pageable) {
        return enrollmentRepository.findAll(tenantId, sessionId, employeeId, status, pageable);
    }
}
