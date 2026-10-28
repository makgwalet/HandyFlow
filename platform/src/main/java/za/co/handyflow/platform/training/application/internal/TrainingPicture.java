package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.CertificateExpiryRules;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingRequirement;
import za.co.handyflow.platform.training.domain.model.TrainingRequirementRules;
import za.co.handyflow.platform.training.domain.model.TrainingRequirementRules.Held;
import za.co.handyflow.platform.training.domain.model.TrainingSelfRules;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCertificateRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingRequirementRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;
import za.co.handyflow.platform.training.dto.MyTrainingResponse;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Required;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads everything the matrix and the team view need about a group of people in a few queries, and works out where each stands on the
 * courses required of them. Reads only; the rules it applies are in {@link TrainingRequirementRules}.
 */
@Component
@RequiredArgsConstructor
class TrainingPicture {

    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingSessionRepository sessionRepository;
    private final TrainingCourseRepository courseRepository;
    private final TrainingCertificateRepository certificateRepository;
    private final TrainingRequirementRepository requirementRepository;

    /** One person's bookings, certificates and standing on each required course. */
    record Person(EmployeeSummary employee, List<MyTrainingResponse.Booking> bookings, List<MyTrainingResponse.Certificate> certificates,
                  Map<UUID, Required> required) {}

    record Picture(Map<UUID, String> courseTitles, List<UUID> requiredCourses, Map<UUID, Person> people) {}

    Picture of(TenantId tenant, List<EmployeeSummary> employees, LocalDate today) {
        List<UUID> ids = employees.stream().map(EmployeeSummary::id).toList();
        List<TrainingRequirement> requirements = requirementRepository.findAllForTenant(tenant);
        List<TrainingEnrollment> enrollments = ids.isEmpty() ? List.of() : enrollmentRepository.findAllForEmployees(tenant, ids);
        List<TrainingCertificate> certificates = ids.isEmpty() ? List.of() : certificateRepository.findAllForEmployees(tenant, ids);

        Map<UUID, TrainingSession> sessions = sessionRepository.findAllById(enrollments.stream().map(TrainingEnrollment::getSessionId).distinct().toList())
                .stream().filter(s -> tenant.getValue().equals(s.getTenantId())).collect(Collectors.toMap(TrainingSession::getId, Function.identity()));
        List<UUID> courseIds = new ArrayList<>(sessions.values().stream().map(TrainingSession::getCourseId).distinct().toList());
        requirements.stream().map(TrainingRequirement::getCourseId).filter(c -> !courseIds.contains(c)).forEach(courseIds::add);
        Map<UUID, String> titles = courseRepository.findAllById(courseIds).stream().filter(c -> tenant.getValue().equals(c.getTenantId()))
                .collect(Collectors.toMap(TrainingCourse::getId, TrainingCourse::getTitle));

        // A certificate belongs to the course of the session it was issued from.
        Map<UUID, UUID> courseOfEnrollment = new HashMap<>();
        for (TrainingEnrollment e : enrollments) {
            TrainingSession s = sessions.get(e.getSessionId());
            if (s != null) courseOfEnrollment.put(e.getId(), s.getCourseId());
        }
        Map<UUID, List<TrainingEnrollment>> enrollmentsOf = enrollments.stream().collect(Collectors.groupingBy(TrainingEnrollment::getEmployeeId));
        Map<UUID, List<TrainingCertificate>> certificatesOf = certificates.stream().collect(Collectors.groupingBy(TrainingCertificate::getEmployeeId));

        List<UUID> requiredCourses = requirements.stream().map(TrainingRequirement::getCourseId).filter(titles::containsKey).distinct().toList();
        Map<UUID, Person> people = new LinkedHashMap<>();
        for (EmployeeSummary emp : employees) {
            List<TrainingEnrollment> mine = enrollmentsOf.getOrDefault(emp.id(), List.of());
            List<TrainingCertificate> held = certificatesOf.getOrDefault(emp.id(), List.of());

            List<MyTrainingResponse.Booking> bookings = mine.stream().map(e -> booking(e, sessions.get(e.getSessionId()), titles)).toList();
            List<MyTrainingResponse.Certificate> certs = held.stream().map(c -> certificate(c, today)).toList();

            Map<UUID, Required> required = new LinkedHashMap<>();
            for (UUID course : requiredCourses) {
                boolean applies = requirements.stream().anyMatch(r -> course.equals(r.getCourseId())
                        && TrainingRequirementRules.applies(r.getJobTitle(), r.getDepartment(), emp.jobTitle(), emp.department()));
                if (!applies) continue;
                List<Held> theirs = held.stream().filter(c -> course.equals(courseOfEnrollment.get(c.getEnrollmentId())))
                        .map(c -> new Held(c.getStatus(), c.getExpiryDate())).toList();
                boolean booked = mine.stream().anyMatch(e -> "ENROLLED".equals(e.getStatus()) && isComing(sessions.get(e.getSessionId()), course, today));
                String status = TrainingRequirementRules.status(theirs, booked, today);
                required.put(course, new Required(course, titles.get(course), status, TrainingRequirementRules.label(status), TrainingRequirementRules.shownExpiry(theirs)));
            }
            people.put(emp.id(), new Person(emp, bookings, certs, required));
        }
        return new Picture(titles, requiredCourses, people);
    }

    private static boolean isComing(TrainingSession s, UUID course, LocalDate today) {
        return s != null && course.equals(s.getCourseId()) && ("SCHEDULED".equals(s.getStatus()) || "IN_PROGRESS".equals(s.getStatus()))
                && s.getEndDate() != null && !s.getEndDate().isBefore(today);
    }

    static MyTrainingResponse.Booking booking(TrainingEnrollment e, TrainingSession s, Map<UUID, String> titles) {
        return new MyTrainingResponse.Booking(e.getId(), s == null ? "Training session" : titles.getOrDefault(s.getCourseId(), "Training session"),
                s == null ? null : s.getStartDate(), s == null ? null : s.getEndDate(), s == null ? null : s.getVenue(),
                e.getStatus(), TrainingSelfRules.bookingLabel(e.getStatus()), e.getScore(), e.getPassed());
    }

    static MyTrainingResponse.Certificate certificate(TrainingCertificate c, LocalDate today) {
        String state = TrainingSelfRules.certificateState(c.getStatus(), c.getExpiryDate(), today);
        Long left = c.getExpiryDate() == null || TrainingSelfRules.REVOKED.equals(state) ? null : CertificateExpiryRules.daysLeft(c.getExpiryDate(), today);
        return new MyTrainingResponse.Certificate(c.getId(), c.getCourseTitleSnapshot(), c.getCertificateNumber(), c.getIssueDate(), c.getExpiryDate(), state, left);
    }

}
