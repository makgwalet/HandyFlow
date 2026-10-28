package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingBookingRules;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingRules;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;

import java.util.List;
import java.util.UUID;

/**
 * Tells employees about their training bookings. Best effort: a message that cannot be sent is logged and never stops the booking
 * step. Only people with an email address on their employee record can be reached.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TrainingNotifier {

    private final NotificationService notificationService;
    private final HrFacade hrFacade;
    private final TrainingCourseRepository courseRepository;

    void enrolled(TenantId tenant, TrainingSession session, TrainingEnrollment enrollment, EmployeeSummary employee) {
        String course = courseTitle(tenant, session);
        send(tenant, NotificationType.TRAINING_ENROLLED, TrainingRules.enrolledTitle(),
                TrainingRules.enrolledMessage(course, session.getStartDate(), session.getVenue()), enrollment, contact(employee));
    }

    void moved(TenantId tenant, TrainingSession session, List<TrainingEnrollment> booked) {
        String course = courseTitle(tenant, session);
        String message = TrainingRules.movedMessage(course, session.getStartDate(), session.getEndDate());
        for (TrainingEnrollment e : booked) send(tenant, NotificationType.TRAINING_SESSION_CHANGED, "Your training session has moved", message, e, contactOf(tenant, e));
    }

    void cancelled(TenantId tenant, TrainingSession session, List<TrainingEnrollment> booked) {
        String course = courseTitle(tenant, session);
        String message = TrainingRules.cancelledMessage(course, session.getStartDate());
        for (TrainingEnrollment e : booked) send(tenant, NotificationType.TRAINING_SESSION_CHANGED, "Your training session was cancelled", message, e, contactOf(tenant, e));
    }

    /** The employee's manager is told that the employee booked or cancelled a place themselves. */
    void tellManager(TenantId tenant, EmployeeSummary employee, TrainingSession session, boolean booked) {
        Recipient to = managerOf(tenant, employee);
        if (to == null) return;
        String course = courseTitle(tenant, session);
        String name = course == null ? "a training session" : course;
        try {
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenant).type(NotificationType.TRAINING_ENROLLED)
                    .title(booked ? employee.fullName() + " booked training" : employee.fullName() + " cancelled a training booking")
                    .message(booked ? TrainingBookingRules.managerBookedMessage(employee.fullName(), name, session.getStartDate())
                                    : TrainingBookingRules.managerCancelledMessage(employee.fullName(), name, session.getStartDate()))
                    .actionUrl("/hr/team-training").sourceModule("training").sourceEntityId(session.getId().toString()).recipients(List.of(to)).build());
        } catch (RuntimeException ex) {
            log.warn("Manager notice about a booking could not be sent: {}", ex.getMessage());
        }
    }

    /** The manager of this employee as a message target, or null when they have none, have left, or cannot be reached. */
    Recipient managerOf(TenantId tenant, EmployeeSummary employee) {
        if (employee == null) return null;
        try {
            UUID managerId = employee.managerId() != null ? employee.managerId()
                    : hrFacade.findLinkedEmployee(tenant, employee.id()).map(EmployeeSummary::managerId).orElse(null);
            if (managerId == null) return null;
            return hrFacade.findLinkedEmployee(tenant, managerId).map(TrainingNotifier::managerContact).orElse(null);
        } catch (RuntimeException ex) { return null; }
    }

    /** A manager is reached in the app through their login when they have one, and by email when they have an address. */
    static Recipient managerContact(EmployeeSummary m) {
        if (m == null || "TERMINATED".equals(m.status())) return null;
        boolean mail = m.email() != null && !m.email().isBlank();
        if (m.userId() != null) return Recipient.user(m.userId(), m.fullName(), mail ? m.email() : null, m.phone());
        return contact(m);
    }

    private String courseTitle(TenantId tenant, TrainingSession session) {
        try {
            return courseRepository.findActiveById(tenant, session.getCourseId()).map(c -> c.getTitle()).orElse(null);
        } catch (RuntimeException ex) { return null; }
    }

    private Recipient contactOf(TenantId tenant, TrainingEnrollment e) {
        try {
            return hrFacade.findEmployeeById(tenant, e.getEmployeeId()).map(TrainingNotifier::contact).orElse(null);
        } catch (RuntimeException ex) { return null; }
    }

    static Recipient contact(EmployeeSummary e) {
        if (e == null || e.email() == null || e.email().isBlank()) return null;
        return Recipient.external(e.fullName(), e.email(), e.phone());
    }

    private void send(TenantId tenant, NotificationType type, String title, String message, TrainingEnrollment enrollment, Recipient to) {
        if (to == null) return;
        try {
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenant).type(type).title(title).message(message).actionUrl("/training")
                    .sourceModule("training").sourceEntityId(enrollment.getId().toString()).recipients(List.of(to)).build());
        } catch (RuntimeException ex) {
            log.warn("Training notification {} could not be sent: {}", type, ex.getMessage());
        }
    }
}
