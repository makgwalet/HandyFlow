package za.co.handyflow.platform.projects.application.internal.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.projects.domain.model.ProjectMember;
import za.co.handyflow.platform.projects.domain.repository.ProjectMemberRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Delivers {@link ProjectEvent}s once the business transaction has committed, in a transaction of its own: the bell for
 * everyone, email for those who have not switched it off for that event. Failures are logged, never thrown.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectNotificationListener {

    private final ProjectMemberRepository members;
    private final UserRecipientResolver users;
    private final NotificationService notifications;
    private final ProjectNotificationSettings settings;

    /** The people an event goes to: named users plus role holders, without the person who caused it. */
    static Set<UUID> recipients(ProjectEvent e, List<ProjectMember> projectMembers) {
        Set<UUID> to = new LinkedHashSet<>(e.users());
        if (!e.roles().isEmpty())
            projectMembers.stream().filter(m -> e.roles().contains(m.getRole())).forEach(m -> to.add(m.getUserId()));
        to.remove(null);
        if (e.actor() != null) to.remove(e.actor());
        return to;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProjectEvent e) {
        try {
            List<ProjectMember> projectMembers = e.roles().isEmpty() ? List.of() : members.findByProject(e.tenantId(), e.projectId());
            Set<UUID> to = recipients(e, projectMembers);
            if (to.isEmpty()) return;
            TenantId tenant = TenantId.of(e.tenantId());
            Set<UUID> email = settings.wantingEmail(e.tenantId(), e.event(), to);
            List<Recipient> withEmail = new ArrayList<>(), bellOnly = new ArrayList<>();
            for (UUID u : to) users.resolveUser(tenant, u).ifPresent(r -> (email.contains(u) ? withEmail : bellOnly).add(r));
            send(e, tenant, withEmail, Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
            send(e, tenant, bellOnly, Set.of(NotificationChannel.IN_APP));
        } catch (RuntimeException ex) {
            log.warn("[PM] Could not deliver {} for project {}: {}", e.event(), e.projectId(), ex.getMessage());
        }
    }

    private void send(ProjectEvent e, TenantId tenant, List<Recipient> to, Set<NotificationChannel> channels) {
        if (to.isEmpty()) return;
        notifications.send(NotificationRequest.builder()
                .tenantId(tenant).type(e.event().type()).channels(channels)
                .title(e.title()).message(e.message())
                .actionUrl("/projects/projects?project=" + e.projectId())
                .sourceModule("projects").sourceEntityId(e.entityId() == null ? e.projectId().toString() : e.entityId().toString())
                .recipients(to).build());
    }
}
