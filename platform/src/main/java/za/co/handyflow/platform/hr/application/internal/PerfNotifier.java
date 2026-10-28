package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrPerfCycle;
import za.co.handyflow.platform.hr.domain.model.HrPerfGoal;
import za.co.handyflow.platform.hr.domain.model.HrPerfReview;
import za.co.handyflow.platform.hr.domain.model.PerfReminderRules;
import za.co.handyflow.platform.hr.domain.model.ReminderKeyRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    /**
     * Remembers the day's reminders. Optional only so the class can be built without a database in tests; in the application it is always
     * present. Without it nothing is held back.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    ReminderLedger ledger;

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    /**
     * Types that only the morning jobs send. Each is sent to a person once a day for the same words: a second run, a restart or a second
     * instance stays quiet. Types that also report something that just happened (a review opened, a goal approved) are not listed, because
     * the same words can be right twice in a day.
     */
    static final Set<NotificationType> DAILY_ONLY = Set.of(NotificationType.ER_CASE_DUE, NotificationType.LEAVE_AT_RISK,
            NotificationType.LETTER_UNACKNOWLEDGED, NotificationType.PERF_GOAL_OVERDUE, NotificationType.PERF_GOAL_WAITING,
            NotificationType.PERF_PLAN_CHECKIN_DUE, NotificationType.PERF_PLAN_ENDED, NotificationType.PERF_REVIEWS_OVERDUE);

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

    /** An employee proposed a goal: their manager is asked to approve it. */
    void goalProposed(TenantId tenant, HrPerfGoal goal, HrEmployee subject) {
        if (subject.getManagerId() == null) return;
        employeeRepo.findActiveById(tenant, subject.getManagerId()).ifPresent(manager ->
                send(tenant, NotificationType.PERF_GOAL_PROPOSED, PerfReminderRules.goalProposedTitle(subject.getFullName()),
                        PerfReminderRules.goalProposedMessage(subject.getFullName(), goal.getTitle()), goal.getId(), "/hr/performance", contactOf(manager)));
    }

    /** A manager approved the employee's goal. */
    void goalApproved(TenantId tenant, HrPerfGoal goal, HrEmployee subject) {
        send(tenant, NotificationType.PERF_GOAL_DECIDED, PerfReminderRules.goalApprovedTitle(),
                PerfReminderRules.goalApprovedMessage(goal.getTitle()), goal.getId(), "/hr/performance", contactOf(subject));
    }

    /** A manager or HR set a goal for the employee. */
    void goalSet(TenantId tenant, HrPerfGoal goal, HrEmployee subject) {
        send(tenant, NotificationType.PERF_GOAL_DECIDED, PerfReminderRules.goalSetTitle(),
                PerfReminderRules.goalSetMessage(goal.getTitle(), goal.getDueDate()), goal.getId(), "/hr/performance", contactOf(subject));
    }

    static Recipient contactOf(HrEmployee e) {
        if (e == null || e.getEmail() == null || e.getEmail().isBlank()) return null;
        return Recipient.external(e.getFullName(), e.getEmail(), e.getPhone());
    }

    void send(TenantId tenant, NotificationType type, String title, String message, UUID ref, String url, Recipient... to) {
        List<Recipient> recipients = java.util.Arrays.stream(to).filter(java.util.Objects::nonNull).toList();
        if (recipients.isEmpty()) return;
        LocalDate day = LocalDate.now(SAST);
        List<String> claimed = new ArrayList<>();
        if (ledger != null && DAILY_ONLY.contains(type)) {
            List<Recipient> fresh = new ArrayList<>();
            for (Recipient r : recipients) {
                String who = ReminderKeyRules.person(r.userId(), r.email(), r.displayName());
                if (who == null) { fresh.add(r); continue; }
                String digest = ReminderKeyRules.key(type.name(), ref.toString(), who, title, message);
                boolean first;
                try {
                    first = ledger.claim(tenant, day, digest);
                } catch (RuntimeException ex) {
                    log.warn("Reminder ledger unavailable, sending anyway: {}", ex.getMessage());
                    first = true;
                }
                if (first) { fresh.add(r); claimed.add(digest); }
            }
            if (fresh.isEmpty()) return;
            recipients = fresh;
        }
        try {
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenant).type(type).title(title).message(message).actionUrl(url)
                    .sourceModule("hr").sourceEntityId(ref.toString()).recipients(recipients).build());
        } catch (RuntimeException ex) {
            log.warn("Performance notification {} could not be sent: {}", type, ex.getMessage());
            if (ledger != null) claimed.forEach(d -> ledger.release(tenant, day, d));
        }
    }

    void send(TenantId tenant, NotificationType type, String title, String message, UUID ref, String url, List<Recipient> to) {
        send(tenant, type, title, message, ref, url, to.toArray(new Recipient[0]));
    }
}
