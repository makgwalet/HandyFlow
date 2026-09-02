package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
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

    @Transactional
    public TrainingEnrollment enroll(TenantId tenantId, UUID sessionId, UUID employeeId, String notes) {
        TrainingSession session = sessionRepository.findByTenantAndId(tenantId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSession", sessionId.toString()));
        if (!session.acceptsEnrollment()) {
            throw new IllegalStateException("Session " + sessionId + " is " + session.getStatus() + " and no longer accepts enrollments");
        }

        EmployeeResponse employee = hrFacade.findEmployeeById(tenantId, employeeId)
                .orElseThrow(() -> new HandyFlowException(
                        "Employee " + employeeId + " was not found in HR", HttpStatus.BAD_REQUEST, "EMPLOYEE_NOT_FOUND"));

        enrollmentRepository.findActiveEnrollment(tenantId, sessionId, employeeId).ifPresent(existing -> {
            throw new IllegalStateException("Employee " + employeeId + " is already enrolled in this session");
        });

        long liveCount = enrollmentRepository.countLiveBySession(tenantId, sessionId);
        if (session.isFull(liveCount)) {
            throw new IllegalStateException("Session " + sessionId + " is at capacity (" + session.getCapacity() + ")");
        }

        TrainingEnrollment enrollment = TrainingEnrollment.create(tenantId, sessionId, employeeId,
                employee.fullName(), employee.employeeNumber(), notes);
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public TrainingEnrollment markAttended(TenantId tenantId, UUID id) {
        TrainingEnrollment enrollment = get(tenantId, id);
        enrollment.markAttended();
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public TrainingEnrollment markNoShow(TenantId tenantId, UUID id) {
        TrainingEnrollment enrollment = get(tenantId, id);
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
        enrollment.complete(score, passed);
        return enrollmentRepository.save(enrollment);
    }

    public TrainingEnrollment get(TenantId tenantId, UUID id) {
        return enrollmentRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingEnrollment", id.toString()));
    }

    public Page<TrainingEnrollment> list(TenantId tenantId, UUID sessionId, UUID employeeId, String status, Pageable pageable) {
        return enrollmentRepository.findAll(tenantId, sessionId, employeeId, status, pageable);
    }
}
