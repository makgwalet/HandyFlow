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
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingCourseService {

    private final TrainingCourseRepository courseRepository;
    private final TrainingNumberGenerator numberGenerator;

    @Transactional
    public TrainingCourse create(TenantId tenantId, String title, String description, String category,
                                  String deliveryMode, BigDecimal durationHours, String defaultTrainerName,
                                  BigDecimal cost, boolean certificationOffered, Integer certificateValidityMonths) {
        String courseCode = numberGenerator.nextCourseCode(tenantId);
        TrainingCourse course = TrainingCourse.create(tenantId, courseCode, title, description, category,
                deliveryMode, durationHours, defaultTrainerName, cost, certificationOffered, certificateValidityMonths);
        return courseRepository.save(course);
    }

    @Transactional
    public TrainingCourse update(TenantId tenantId, UUID id, String title, String description, String category,
                                  String deliveryMode, BigDecimal durationHours, String defaultTrainerName,
                                  BigDecimal cost, boolean certificationOffered, Integer certificateValidityMonths) {
        TrainingCourse course = getActive(tenantId, id);
        course.update(title, description, category, deliveryMode, durationHours, defaultTrainerName, cost,
                certificationOffered, certificateValidityMonths);
        return courseRepository.save(course);
    }

    @Transactional
    public TrainingCourse archive(TenantId tenantId, UUID id) {
        TrainingCourse course = getActive(tenantId, id);
        course.archive();
        return courseRepository.save(course);
    }

    @Transactional
    public TrainingCourse reactivate(TenantId tenantId, UUID id) {
        TrainingCourse course = getActive(tenantId, id);
        course.reactivate();
        return courseRepository.save(course);
    }

    @Transactional
    public void softDelete(TenantId tenantId, UUID id) {
        TrainingCourse course = getActive(tenantId, id);
        course.softDelete();
        courseRepository.save(course);
    }

    public TrainingCourse get(TenantId tenantId, UUID id) {
        return getActive(tenantId, id);
    }

    public Page<TrainingCourse> list(TenantId tenantId, String status, String search, Pageable pageable) {
        return courseRepository.findAllActive(tenantId, status, search, pageable);
    }

    private TrainingCourse getActive(TenantId tenantId, UUID id) {
        return courseRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingCourse", id.toString()));
    }
}
