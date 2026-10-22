package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrPerfCycle;
import za.co.handyflow.platform.hr.domain.model.HrPerfReview;
import za.co.handyflow.platform.hr.domain.model.PerfReminderRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Tells people it is their turn in a performance review. Best effort: a notification that cannot be sent is logged and never
 * stops the review step that caused it. The messages say whose turn it is and nothing about ratings or comments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PerfNotifier {

    private final NotificationService notificationService;
    private final HrEmployeeRepository employeeRepo;

    /** A cycle was opened (or someone joined it): each employee is asked for their self-review. */
    void selfReviewAsked(TenantId tenant, HrPerfCycle cycle, List<HrPerfReview> reviews) {
        for (HrPerfReview r : reviews) {
            employeeRepo.findActiveById(tenant, r.getEmployeeId()).ifPresent(e ->
                    send(tenant, NotificationType.PERF_SELF_REVIEW_DUE, PerfReminderRules.selfDueTitle(cycle.getName()),
                            PerfReminderRules.selfOpenedMessage(cycle.getName(), cycle.getSelfDue()), r.getId(), "/hr/performance", contactOf(e)));
        }
    }

    /** The employee submitted their self-review: the reviewer is told it is their turn. */
    void readyForReviewer(TenantId tenant, HrPerfCycle cycle, HrPerfReview review, HrEmployee subject) {
        if (review.getReviewerId() == null) return;
        employeeRepo.findActiveById(tenant, review.getReviewerId()).ifPresent(reviewer ->
                send(tenant, NotificationType.PERF_READY_FOR_MANAGER, PerfReminderRules.readyTitle(subject.getFullName()),
                        PerfReminderRules.readyMessage(subject.getFullName(), cycle.getName(), cycle.getManagerDue()), review.getId(), "/hr/performance", contactOf(reviewer)));
    }

    /** HR released the result: the employee is told to read it. */
    void resultReleased(TenantId tenant, HrPerfCycle cycle, HrPerfReview review, HrEmployee subject) {
        send(tenant, NotificationType.PERF_RESULT_RELEASED, PerfReminderRules.releasedTitle(cycle.getName()),
                PerfReminderRules.releasedMessage(cycle.getName()), review.getId(), "/hr/performance", contactOf(subject));
    }

    /** People were asked for 360 feedback. */
    void feedbackAsked(TenantId tenant, HrPerfCycle cycle, HrEmployee subject, HrPerfReview review, List<UUID> providerIds) {
        for (UUID id : providerIds) {
            employeeRepo.findActiveById(tenant, id).ifPresent(p ->
                    send(tenant, NotificationType.PERF_FEEDBACK_REQUESTED, PerfReminderRules.feedbackTitle(subject.getFullName()),
                            PerfReminderRules.feedbackMessage(subject.getFullName(), cycle.getName()), review.getId(), "/hr/performance", contactOf(p)));
        }
    }

    static Recipient contactOf(HrEmployee e) {
        if (e == null || e.getEmail() == null || e.getEmail().isBlank()) return null;
        return Recipient.external(e.getFullName(), e.getEmail(), e.getPhone());
    }

    void send(TenantId tenant, NotificationType type, String title, String message, UUID ref, String url, Recipient... to) {
        List<Recipient> recipients = java.util.Arrays.stream(to).filter(java.util.Objects::nonNull).toList();
        if (recipients.isEmpty()) return;
        try {
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenant).type(type).title(title).message(message).actionUrl(url)
                    .sourceModule("hr").sourceEntityId(ref.toString()).recipients(recipients).build());
        } catch (RuntimeException ex) {
            log.warn("Performance notification {} could not be sent: {}", type, ex.getMessage());
        }
    }

    void send(TenantId tenant, NotificationType type, String title, String message, UUID ref, String url, List<Recipient> to) {
        send(tenant, type, title, message, ref, url, to.toArray(new Recipient[0]));
    }
}
