package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.CertificateExpiryRules;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingSelfRules;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCertificateRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;
import za.co.handyflow.platform.training.dto.MyTrainingResponse;
import za.co.handyflow.platform.training.dto.MyTrainingResponse.Booking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An employee's own bookings and certificates, found through the employee record linked to their login. Nothing here takes an employee
 * id from the request, so nobody can ask for someone else's.
 */
@Service
@RequiredArgsConstructor
public class TrainingSelfService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final HrFacade hrFacade;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingSessionRepository sessionRepository;
    private final TrainingCourseRepository courseRepository;
    private final TrainingCertificateRepository certificateRepository;

    @Transactional(readOnly = true)
    public MyTrainingResponse mine(TenantId tenantId, UUID userId) {
        EmployeeSummary me = me(tenantId, userId);
        LocalDate today = LocalDate.now(SAST);

        List<TrainingEnrollment> enrollments = enrollmentRepository.findAllForEmployee(tenantId, me.id());
        Map<UUID, TrainingSession> sessions = sessionRepository.findAllById(enrollments.stream().map(TrainingEnrollment::getSessionId).distinct().toList())
                .stream().filter(s -> tenantId.getValue().equals(s.getTenantId())).collect(Collectors.toMap(TrainingSession::getId, Function.identity()));
        Map<UUID, String> titles = courseRepository.findAllById(sessions.values().stream().map(TrainingSession::getCourseId).distinct().toList())
                .stream().filter(c -> tenantId.getValue().equals(c.getTenantId())).collect(Collectors.toMap(TrainingCourse::getId, TrainingCourse::getTitle));

        List<Booking> bookings = enrollments.stream().map(e -> {
            TrainingSession s = sessions.get(e.getSessionId());
            return new Booking(e.getId(), s == null ? "Training session" : titles.getOrDefault(s.getCourseId(), "Training session"),
                    s == null ? null : s.getStartDate(), s == null ? null : s.getEndDate(), s == null ? null : s.getVenue(),
                    e.getStatus(), TrainingSelfRules.bookingLabel(e.getStatus()), e.getScore(), e.getPassed());
        }).toList();

        List<MyTrainingResponse.Certificate> certificates = certificateRepository.findAll(tenantId, me.id(), null, Pageable.unpaged()).getContent().stream()
                .map(c -> {
                    String state = TrainingSelfRules.certificateState(c.getStatus(), c.getExpiryDate(), today);
                    Long left = c.getExpiryDate() == null || TrainingSelfRules.REVOKED.equals(state) ? null : CertificateExpiryRules.daysLeft(c.getExpiryDate(), today);
                    return new MyTrainingResponse.Certificate(c.getId(), c.getCourseTitleSnapshot(), c.getCertificateNumber(), c.getIssueDate(), c.getExpiryDate(), state, left);
                }).toList();
        return new MyTrainingResponse(bookings, certificates);
    }

    /** One of the caller's own certificates, for the PDF. Anyone else's looks the same as one that does not exist. */
    @Transactional(readOnly = true)
    public TrainingCertificate ownCertificate(TenantId tenantId, UUID userId, UUID certificateId) {
        EmployeeSummary me = me(tenantId, userId);
        return certificateRepository.findByTenantAndId(tenantId, certificateId)
                .filter(c -> me.id().equals(c.getEmployeeId()) && !"REVOKED".equals(c.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("TrainingCertificate", certificateId.toString()));
    }

    private EmployeeSummary me(TenantId tenantId, UUID userId) {
        return hrFacade.findEmployeeByUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee record for your login", String.valueOf(userId)));
    }
}
