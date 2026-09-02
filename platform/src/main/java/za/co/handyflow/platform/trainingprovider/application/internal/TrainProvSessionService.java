package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCourse;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvSession;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvClientRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvCourseRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvSessionRepository;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainProvSessionService {

    private final TrainProvSessionRepository sessionRepository;
    private final TrainProvCourseRepository courseRepository;
    private final TrainProvClientRepository clientRepository;

    @Transactional
    public TrainProvSession create(TenantId tenantId, UUID courseId, String sessionType, UUID clientId,
                                    LocalDate startDate, LocalDate endDate, String venue, String trainerName,
                                    Integer capacity, String notes) {
        TrainProvCourse course = courseRepository.findActiveById(tenantId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvCourse", courseId.toString()));
        if (!course.isActive()) {
            throw new IllegalStateException("Cannot schedule a session for an archived course");
        }
        if ("CLOSED".equals(sessionType)) {
            TrainProvClient client = clientRepository.findActiveById(tenantId, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", String.valueOf(clientId)));
            if (!"ACTIVE".equals(client.getStatus())) {
                throw new IllegalStateException("Cannot schedule a closed session for an inactive client");
            }
        }
        TrainProvSession session = TrainProvSession.create(tenantId, courseId, sessionType, clientId, startDate,
                endDate, venue, trainerName, capacity, notes);
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainProvSession update(TenantId tenantId, UUID id, String venue, String trainerName, Integer capacity, String notes) {
        TrainProvSession session = get(tenantId, id);
        session.update(venue, trainerName, capacity, notes);
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainProvSession reschedule(TenantId tenantId, UUID id, LocalDate newStart, LocalDate newEnd) {
        TrainProvSession session = get(tenantId, id);
        session.reschedule(newStart, newEnd);
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainProvSession start(TenantId tenantId, UUID id) {
        TrainProvSession session = get(tenantId, id);
        session.start();
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainProvSession complete(TenantId tenantId, UUID id) {
        TrainProvSession session = get(tenantId, id);
        session.complete();
        return sessionRepository.save(session);
    }

    @Transactional
    public TrainProvSession cancel(TenantId tenantId, UUID id, String reason) {
        TrainProvSession session = get(tenantId, id);
        session.cancel(reason);
        return sessionRepository.save(session);
    }

    public TrainProvSession get(TenantId tenantId, UUID id) {
        return sessionRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvSession", id.toString()));
    }

    public Page<TrainProvSession> list(TenantId tenantId, UUID courseId, UUID clientId, String status, Pageable pageable) {
        return sessionRepository.findAll(tenantId, courseId, clientId, status, pageable);
    }
}
