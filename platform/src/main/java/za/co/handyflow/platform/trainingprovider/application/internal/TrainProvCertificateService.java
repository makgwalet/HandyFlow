package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainProvCertificateService {

    private final TrainProvCertificateRepository certificateRepository;
    private final TrainProvEnrollmentRepository enrollmentRepository;
    private final TrainProvSessionRepository sessionRepository;
    private final TrainProvCourseRepository courseRepository;
    private final TrainProvClientRepository clientRepository;
    private final TrainProvNumberGenerator numberGenerator;

    @Transactional
    public TrainProvCertificate issue(TenantId tenantId, UUID enrollmentId) {
        TrainProvEnrollment enrollment = enrollmentRepository.findByTenantAndId(tenantId, enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvEnrollment", enrollmentId.toString()));

        if (!enrollment.isEligibleForCertificate()) {
            throw new IllegalStateException("Enrollment " + enrollmentId + " is " + enrollment.getStatus()
                    + " — a certificate can only be issued for a COMPLETED enrollment");
        }
        certificateRepository.findByEnrollmentId(tenantId, enrollmentId).ifPresent(existing -> {
            throw new IllegalStateException("A certificate has already been issued for enrollment " + enrollmentId);
        });

        TrainProvSession session = sessionRepository.findByTenantAndId(tenantId, enrollment.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvSession", enrollment.getSessionId().toString()));
        TrainProvCourse course = courseRepository.findActiveById(tenantId, session.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvCourse", session.getCourseId().toString()));
        if (!course.isCertificationOffered()) {
            throw new IllegalStateException("Course '" + course.getTitle() + "' does not offer certification");
        }
        TrainProvClient client = clientRepository.findActiveById(tenantId, enrollment.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", enrollment.getClientId().toString()));

        LocalDate issueDate = LocalDate.now();
        LocalDate expiryDate = course.getCertificateValidityMonths() != null
                ? issueDate.plusMonths(course.getCertificateValidityMonths())
                : null;

        String certificateNumber = numberGenerator.nextCertificateNumber(tenantId);
        TrainProvCertificate certificate = TrainProvCertificate.create(tenantId, enrollmentId, enrollment.getDelegateId(),
                enrollment.getClientId(), enrollment.getDelegateNameSnapshot(), client.getTradingName(),
                course.getTitle(), course.getUnitStandardNumber(), certificateNumber, issueDate, expiryDate);
        return certificateRepository.save(certificate);
    }

    @Transactional
    public TrainProvCertificate revoke(TenantId tenantId, UUID id, String reason) {
        TrainProvCertificate certificate = get(tenantId, id);
        certificate.revoke(reason);
        return certificateRepository.save(certificate);
    }

    public TrainProvCertificate get(TenantId tenantId, UUID id) {
        return certificateRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvCertificate", id.toString()));
    }

    public Page<TrainProvCertificate> list(TenantId tenantId, UUID clientId, String status, Pageable pageable) {
        return certificateRepository.findAll(tenantId, clientId, status, pageable);
    }
}
