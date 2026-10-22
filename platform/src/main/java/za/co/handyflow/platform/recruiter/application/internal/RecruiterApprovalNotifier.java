package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterApprovalRules;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Tells approvers a job or offer is waiting for them, and tells the person who asked how it went. Best effort: a message that cannot
 * be sent is logged and never stops the approval step. Approvers are the company's administrators other than the person who asked,
 * because the rules do not let anyone approve their own request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RecruiterApprovalNotifier {

    private final NotificationService notificationService;
    private final TenantAdminRecipients admins;
    private final UserRecipientResolver users;

    void requested(TenantId tenant, boolean job, UUID refId, String jobTitle, UUID submitter) {
        try {
            String submitterName = submitter == null ? null : users.resolveUser(tenant, submitter).map(Recipient::displayName).orElse(null);
            List<Recipient> approvers = admins.resolveTenantAdmins(tenant).stream()
                    .filter(r -> r.userId() == null || !r.userId().equals(submitter)).toList();
            send(tenant, NotificationType.REC_APPROVAL_REQUESTED, RecruiterApprovalRules.requestedTitle(job),
                    RecruiterApprovalRules.requestedMessage(job, jobTitle, submitterName), refId, job, approvers);
        } catch (RuntimeException ex) {
            log.warn("Recruiter approval request could not be announced: {}", ex.getMessage());
        }
    }

    void decided(TenantId tenant, boolean job, UUID refId, String jobTitle, UUID submitter, UUID decider, boolean approved) {
        if (submitter == null) return;
        try {
            String deciderName = decider == null ? null : users.resolveUser(tenant, decider).map(Recipient::displayName).orElse(null);
            List<Recipient> to = users.resolveUser(tenant, submitter).map(List::of).orElse(List.of());
            send(tenant, NotificationType.REC_APPROVAL_DECIDED, RecruiterApprovalRules.decidedTitle(job, approved),
                    RecruiterApprovalRules.decidedMessage(job, approved, jobTitle, deciderName), refId, job, to);
        } catch (RuntimeException ex) {
            log.warn("Recruiter approval outcome could not be announced: {}", ex.getMessage());
        }
    }

    private void send(TenantId tenant, NotificationType type, String title, String message, UUID ref, boolean job, List<Recipient> to) {
        if (to.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenant).type(type).title(title).message(message).actionUrl("/recruiter")
                .sourceModule("recruiter").sourceEntityId(ref.toString()).recipients(to).build());
    }
}
