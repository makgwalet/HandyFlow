package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvEnrollment;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSession;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvDelegateRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvEnrollmentRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvSessionRepository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The centerpiece service — owns the capacity check (live count, same
 * "no denormalized tally" reasoning as Module 4a's own
 * TrainingEnrollmentService) and, for a CLOSED session, enforces that
 * an enrolling delegate actually belongs to that session's own client
 * — the service-layer half of the rule {@code TrainProvSession}'s own
 * Javadoc documents as NOT enforced at the DB level.
 */
@Service
@RequiredArgsConstructor
public class TrainProvEnrollmentService {

    private final TrainProvEnrollmentRepository enrollmentRepository;
    private final TrainProvSessionRepository sessionRepository;
    private final TrainProvDelegateRepository delegateRepository;

    @Transactional
    public TrainProvEnrollment enroll(TenantId tenantId, UUID sessionId, UUID delegateId, String notes) {
        TrainProvSession session = sessionRepository.findByTenantAndId(tenantId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvSession", sessionId.toString()));
        if (!session.acceptsEnrollment()) {
            throw new IllegalStateException("Session " + sessionId + " is " + session.getStatus() + " and no longer accepts enrollments");
        }

        TrainProvDelegate delegate = delegateRepository.findActiveById(tenantId, delegateId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvDelegate", delegateId.toString()));
        if (!"ACTIVE".equals(delegate.getStatus())) {
            throw new IllegalStateException("Delegate " + delegateId + " is not active");
        }

        if (session.isClosed() && !session.getClientId().equals(delegate.getClientId())) {
            throw new IllegalStateException(
                    "Session " + sessionId + " is a closed in-house session for a different client — this delegate cannot be enrolled");
        }

        enrollmentRepository.findActiveEnrollment(tenantId, sessionId, delegateId).ifPresent(existing -> {
            throw new IllegalStateException("Delegate " + delegateId + " is already enrolled in this session");
        });

        long liveCount = enrollmentRepository.countLiveBySession(tenantId, sessionId);
        if (session.isFull(liveCount)) {
            throw new IllegalStateException("Session " + sessionId + " is at capacity (" + session.getCapacity() + ")");
        }

        TrainProvEnrollment enrollment = TrainProvEnrollment.create(tenantId, sessionId, delegateId,
                delegate.getClientId(), delegate.getFullName(), notes);
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public TrainProvEnrollment markAttended(TenantId tenantId, UUID id) {
        TrainProvEnrollment enrollment = get(tenantId, id);
        enrollment.markAttended();
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public TrainProvEnrollment markNoShow(TenantId tenantId, UUID id) {
        TrainProvEnrollment enrollment = get(tenantId, id);
        enrollment.markNoShow();
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public TrainProvEnrollment cancel(TenantId tenantId, UUID id, String reason) {
        TrainProvEnrollment enrollment = get(tenantId, id);
        enrollment.cancel(reason);
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public TrainProvEnrollment complete(TenantId tenantId, UUID id, BigDecimal score, boolean passed) {
        TrainProvEnrollment enrollment = get(tenantId, id);
        enrollment.complete(score, passed);
        return enrollmentRepository.save(enrollment);
    }

    public TrainProvEnrollment get(TenantId tenantId, UUID id) {
        return enrollmentRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvEnrollment", id.toString()));
    }

    public Page<TrainProvEnrollment> list(TenantId tenantId, UUID sessionId, UUID clientId, String status, Pageable pageable) {
        return enrollmentRepository.findAll(tenantId, sessionId, clientId, status, pageable);
    }
}
