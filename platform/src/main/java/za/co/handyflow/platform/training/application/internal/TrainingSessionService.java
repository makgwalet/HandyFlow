package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;

import java.time.LocalDate;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingRules;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingSessionService {

    private final TrainingSessionRepository sessionRepository;
    private final TrainingCourseRepository courseRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingNotifier notifier;

    public TrainingSession create(TenantId tenantId, UUID courseId, LocalDate startDate, LocalDate endDate,
                                   String venue, String trainerName, Integer capacity, String notes) {
        return create(tenantId, courseId, startDate, endDate, venue, trainerName, capacity, notes, null);
    }

    @Transactional
    public TrainingSession create(TenantId tenantId, UUID courseId, LocalDate startDate, LocalDate endDate,
                                   String venue, String trainerName, Integer capacity, String notes, Boolean openBooking) {
        TrainingCourse course = courseRepository.findActiveById(tenantId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingCourse", courseId.toString()));
        if (!course.isActive()) {
            throw new IllegalStateException("Cannot schedule a session for an archived course");
        }
        // Falls back to the course's own default trainer when the session doesn't name one.
        String effectiveTrainer = (trainerName != null && !trainerName.isBlank()) ? trainerName : course.getDefaultTrainerName();
        TrainingSession session = TrainingSession.create(tenantId, courseId, startDate, endDate, venue, effectiveTrainer, capacity, notes);
        if (Boolean.TRUE.equals(openBooking)) session.setOpenBooking(true);
        return sessionRepository.save(session);
    }

    public TrainingSession update(TenantId tenantId, UUID id, String venue, String trainerName, Integer capacity, String notes) {
        return update(tenantId, id, venue, trainerName, capacity, notes, null);
    }

    /** {@code openBooking} null leaves the setting as it is. */
    @Transactional
    public TrainingSession update(TenantId tenantId, UUID id, String venue, String trainerName, Integer capacity, String notes, Boolean openBooking) {
        TrainingSession session = get(tenantId, id);
        String problem = TrainingRules.capacityProblem(capacity, enrollmentRepository.countLiveBySession(tenantId, id));
        if (problem != null) throw new IllegalArgumentException(problem);
        session.update(venue, trainerName, capacity, notes);
        if (openBooking != null) session.setOpenBooking(openBooking);
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainingSession reschedule(TenantId tenantId, UUID id, LocalDate newStart, LocalDate newEnd) {
        TrainingSession session = get(tenantId, id);
        session.reschedule(newStart, newEnd);
        TrainingSession saved = sessionRepository.save(session);
        notifier.moved(tenantId, saved, booked(tenantId, id));
        return saved;
    }

    @Transactional
    public TrainingSession start(TenantId tenantId, UUID id) {
        TrainingSession session = get(tenantId, id);
        session.start();
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainingSession complete(TenantId tenantId, UUID id) {
        TrainingSession session = get(tenantId, id);
        String problem = TrainingRules.sessionCompleteProblem(enrollmentRepository.findAllForSession(tenantId, id).stream()
                .filter(e -> "ENROLLED".equals(e.getStatus())).count());
        if (problem != null) throw new IllegalStateException(problem);
        session.complete();
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainingSession cancel(TenantId tenantId, UUID id, String reason) {
        TrainingSession session = get(tenantId, id);
        session.cancel(reason);
        // People still waiting for the session are released with it; those who already attended keep their record.
        List<TrainingEnrollment> released = booked(tenantId, id).stream().filter(e -> "ENROLLED".equals(e.getStatus())).toList();
        for (TrainingEnrollment e : released) {
            e.cancel("Session cancelled" + (reason == null || reason.isBlank() ? "" : ": " + reason.trim()));
            enrollmentRepository.save(e);
        }
        TrainingSession saved = sessionRepository.save(session);
        notifier.cancelled(tenantId, saved, released);
        return saved;
    }

    /** People still booked on the session (enrolled or attended). */
    private List<TrainingEnrollment> booked(TenantId tenantId, UUID sessionId) {
        return enrollmentRepository.findAllForSession(tenantId, sessionId).stream()
                .filter(e -> "ENROLLED".equals(e.getStatus()) || "ATTENDED".equals(e.getStatus())).toList();
    }

    public TrainingSession get(TenantId tenantId, UUID id) {
        return sessionRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSession", id.toString()));
    }

    public Page<TrainingSession> list(TenantId tenantId, UUID courseId, String status, Pageable pageable) {
        return sessionRepository.findAll(tenantId, courseId, status, pageable);
    }
}
