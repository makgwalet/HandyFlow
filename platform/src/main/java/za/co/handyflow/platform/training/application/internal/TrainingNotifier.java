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
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.model.TrainingRules;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;

import java.util.List;

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
