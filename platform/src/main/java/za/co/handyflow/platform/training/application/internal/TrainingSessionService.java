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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingSessionService {

    private final TrainingSessionRepository sessionRepository;
    private final TrainingCourseRepository courseRepository;

    @Transactional
    public TrainingSession create(TenantId tenantId, UUID courseId, LocalDate startDate, LocalDate endDate,
                                   String venue, String trainerName, Integer capacity, String notes) {
        TrainingCourse course = courseRepository.findActiveById(tenantId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingCourse", courseId.toString()));
        if (!course.isActive()) {
            throw new IllegalStateException("Cannot schedule a session for an archived course");
        }
        // Falls back to the course's own default trainer when the session doesn't name one.
        String effectiveTrainer = (trainerName != null && !trainerName.isBlank()) ? trainerName : course.getDefaultTrainerName();
        TrainingSession session = TrainingSession.create(tenantId, courseId, startDate, endDate, venue, effectiveTrainer, capacity, notes);
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainingSession update(TenantId tenantId, UUID id, String venue, String trainerName, Integer capacity, String notes) {
        TrainingSession session = get(tenantId, id);
        session.update(venue, trainerName, capacity, notes);
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainingSession reschedule(TenantId tenantId, UUID id, LocalDate newStart, LocalDate newEnd) {
        TrainingSession session = get(tenantId, id);
        session.reschedule(newStart, newEnd);
        return sessionRepository.save(session);
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
        session.complete();
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainingSession cancel(TenantId tenantId, UUID id, String reason) {
        TrainingSession session = get(tenantId, id);
        session.cancel(reason);
        return sessionRepository.save(session);
    }

    public TrainingSession get(TenantId tenantId, UUID id) {
        return sessionRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingSession", id.toString()));
    }

    public Page<TrainingSession> list(TenantId tenantId, UUID courseId, String status, Pageable pageable) {
        return sessionRepository.findAll(tenantId, courseId, status, pageable);
    }
}
