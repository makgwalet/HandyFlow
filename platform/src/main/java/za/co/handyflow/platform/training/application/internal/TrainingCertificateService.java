package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCertificateRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingCertificateService {

    private final TrainingCertificateRepository certificateRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingSessionRepository sessionRepository;
    private final TrainingCourseRepository courseRepository;
    private final TrainingNumberGenerator numberGenerator;

    @Transactional
    public TrainingCertificate issue(TenantId tenantId, UUID enrollmentId) {
        TrainingEnrollment enrollment = enrollmentRepository.findByTenantAndId(tenantId, enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingEnrollment", enrollmentId.toString()));

        if (!enrollment.isEligibleForCertificate()) {
            throw new IllegalStateException("Enrollment " + enrollmentId + " is " + enrollment.getStatus()
                    + " — a certificate can only be issued for a COMPLETED enrollment");
        }
        certificateRepository.findByEnrollmentId(tenantId, enrollmentId).ifPresent(existing -> {
            throw new IllegalStateException("A certificate has already been issued for enrollment " + enrollmentId);
        });

        TrainingSession session = sessionRepository.findByTenantAndId(tenantId, enrollment.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSession", enrollment.getSessionId().toString()));
        TrainingCourse course = courseRepository.findActiveById(tenantId, session.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainingCourse", session.getCourseId().toString()));

        if (!course.isCertificationOffered()) {
            throw new IllegalStateException("Course '" + course.getTitle() + "' does not offer certification");
        }

        LocalDate issueDate = LocalDate.now();
        LocalDate expiryDate = course.getCertificateValidityMonths() != null
                ? issueDate.plusMonths(course.getCertificateValidityMonths())
                : null;

        String certificateNumber = numberGenerator.nextCertificateNumber(tenantId);
        TrainingCertificate certificate = TrainingCertificate.create(tenantId, enrollmentId, enrollment.getEmployeeId(),
                enrollment.getEmployeeNameSnapshot(), course.getTitle(), certificateNumber, issueDate, expiryDate);
        return certificateRepository.save(certificate);
    }

    @Transactional
    public TrainingCertificate revoke(TenantId tenantId, UUID id, String reason) {
        TrainingCertificate certificate = get(tenantId, id);
        certificate.revoke(reason);
        return certificateRepository.save(certificate);
    }

    public TrainingCertificate get(TenantId tenantId, UUID id) {
        return certificateRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingCertificate", id.toString()));
    }

    public Page<TrainingCertificate> list(TenantId tenantId, UUID employeeId, String status, Pageable pageable) {
        return certificateRepository.findAll(tenantId, employeeId, status, pageable);
    }
}
