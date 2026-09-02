package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvCourse;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvCourseRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainProvCourseService {

    private final TrainProvCourseRepository courseRepository;
    private final TrainProvNumberGenerator numberGenerator;

    @Transactional
    public TrainProvCourse create(TenantId tenantId, String title, String description, String unitStandardNumber,
                                   Integer nqfLevel, Integer credits, BigDecimal durationDays,
                                   BigDecimal pricePerDelegate, boolean certificationOffered,
                                   Integer certificateValidityMonths) {
        String courseCode = numberGenerator.nextCourseCode(tenantId);
        TrainProvCourse course = TrainProvCourse.create(tenantId, courseCode, title, description, unitStandardNumber,
                nqfLevel, credits, durationDays, pricePerDelegate, certificationOffered, certificateValidityMonths);
        return courseRepository.save(course);
    }

    @Transactional
    public TrainProvCourse update(TenantId tenantId, UUID id, String title, String description,
                                   String unitStandardNumber, Integer nqfLevel, Integer credits,
                                   BigDecimal durationDays, BigDecimal pricePerDelegate,
                                   boolean certificationOffered, Integer certificateValidityMonths) {
        TrainProvCourse course = getActive(tenantId, id);
        course.update(title, description, unitStandardNumber, nqfLevel, credits, durationDays, pricePerDelegate,
                certificationOffered, certificateValidityMonths);
        return courseRepository.save(course);
    }

    @Transactional
    public TrainProvCourse archive(TenantId tenantId, UUID id) {
        TrainProvCourse course = getActive(tenantId, id);
        course.archive();
        return courseRepository.save(course);
    }

    @Transactional
    public TrainProvCourse reactivate(TenantId tenantId, UUID id) {
        TrainProvCourse course = getActive(tenantId, id);
        course.reactivate();
        return courseRepository.save(course);
    }

    @Transactional
    public void softDelete(TenantId tenantId, UUID id) {
        TrainProvCourse course = getActive(tenantId, id);
        course.softDelete();
        courseRepository.save(course);
    }

    public TrainProvCourse get(TenantId tenantId, UUID id) {
        return getActive(tenantId, id);
    }

    public Page<TrainProvCourse> list(TenantId tenantId, String status, String search, Pageable pageable) {
        return courseRepository.findAllActive(tenantId, status, search, pageable);
    }

    private TrainProvCourse getActive(TenantId tenantId, UUID id) {
        return courseRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvCourse", id.toString()));
    }
}
