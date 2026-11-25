package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Tells people when contact happens. Always after the change is saved, and never able to undo it: a failure here is logged and nothing more.
 * Who is told on the practice's side: whoever has already replied on the thread; otherwise the people chosen for the daily digest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceContactNotifier {

    private final NotificationService notifications;
    private final UserRecipientResolver resolver;
    private final ComplianceReminderRecipientRepository digestRecipients;
    private final CompliancePortalMessageRepository messages;
    private final PortalUserRepository portalUsers;

    public void clientWrote(TenantId tenantId, CompliancePortalQuery q, String clientName, boolean first) {
        later("client message", () -> {
            Set<UUID> people = new LinkedHashSet<>();
            for (CompliancePortalMessage m : messages.findForQuery(tenantId, q.getId())) {
                if ("STAFF".equals(m.getAuthorType()) && m.getAuthorId() != null) people.add(m.getAuthorId());
            }
            if (people.isEmpty()) for (ComplianceReminderRecipient r : digestRecipients.findForTenant(tenantId)) people.add(r.getUserId());
            tellStaff(tenantId, people, NotificationType.COMPLIANCESERVICES_CLIENT_MESSAGE, ContactNotices.clientWroteTitle(clientName, first),
                    ContactNotices.clientWroteMessage(clientName, q.getSubject(), first), "/complianceservices/requests", q.getId().toString());
        });
    }

    public void fileReceived(TenantId tenantId, ComplianceDocumentRequest r, String clientName) {
        later("client upload", () -> {
            Set<UUID> people = new LinkedHashSet<>();
            if (r.getRequestedBy() != null) people.add(r.getRequestedBy());
            else for (ComplianceReminderRecipient d : digestRecipients.findForTenant(tenantId)) people.add(d.getUserId());
            tellStaff(tenantId, people, NotificationType.COMPLIANCESERVICES_CLIENT_UPLOAD, ContactNotices.uploadTitle(clientName),
                    ContactNotices.uploadMessage(clientName, r.getTitle()), "/complianceservices/clients/" + r.getClientId() + "?tab=requests", r.getId().toString());
        });
    }

    /** The client who raised the query is told by email that there is a reply, without what it says. */
    public void practiceReplied(TenantId tenantId, CompliancePortalQuery q) {
        later("practice reply", () -> {
            var person = portalUsers.findById(q.getRaisedBy());
            if (person.isEmpty() || person.get().getEmail() == null) return;
            notifications.send(NotificationRequest.builder().tenantId(tenantId).type(NotificationType.COMPLIANCESERVICES_PRACTICE_REPLY)
                    .title(ContactNotices.replyTitle()).message(ContactNotices.replyMessage(q.getSubject())).sourceModule("complianceservices").sourceEntityId(q.getId().toString())
                    .recipients(List.of(Recipient.external(person.get().getFullName(), person.get().getEmail(), null))).build());
        });
    }

    private void tellStaff(TenantId tenantId, Set<UUID> people, NotificationType type, String title, String message, String url, String entityId) {
        List<Recipient> to = new ArrayList<>();
        for (UUID id : people) resolver.resolveUser(tenantId, id).ifPresent(to::add);
        if (to.isEmpty()) return;
        notifications.send(NotificationRequest.builder().tenantId(tenantId).type(type).title(title).message(message).actionUrl(url)
                .sourceModule("complianceservices").sourceEntityId(entityId).recipients(to).build());
    }

    /** After the surrounding save commits, or straight away when there is no save in progress. */
    private void later(String what, Runnable work) {
        Runnable safe = () -> {
            try { work.run(); } catch (RuntimeException e) { log.warn("[ComplianceServices] could not hand over the {} notice: {}", what, e.getMessage()); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safe.run(); }
            });
        } else {
            safe.run();
        }
    }
}
