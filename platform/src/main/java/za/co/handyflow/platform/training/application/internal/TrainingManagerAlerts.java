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
import za.co.handyflow.platform.training.domain.model.CertificateDigestRules;
import za.co.handyflow.platform.training.domain.model.CertificateDigestRules.Item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells managers about certificates in their team that are about to expire or have expired: one message per manager for the day, listing
 * the people, rather than one for each certificate. Best effort; a message that cannot be sent is logged and never stops the daily sweep.
 * A person with no manager, or whose manager has left or cannot be reached, is simply not covered here (the administrators are told as before).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TrainingManagerAlerts {

    private final HrFacade hrFacade;
    private final NotificationService notificationService;

    /** A certificate that is about to expire or has expired, with its holder. */
    record Case(UUID employeeId, Item item) {}

    void tell(TenantId tenant, List<Case> expiring, List<Case> expired) {
        Map<UUID, UUID> managerOf = new LinkedHashMap<>();   // employee -> manager (null: none)
        Map<UUID, Recipient> reach = new LinkedHashMap<>();   // manager -> how to reach them (null: cannot)
        send(tenant, false, group(tenant, expiring, managerOf, reach), reach);
        send(tenant, true, group(tenant, expired, managerOf, reach), reach);
    }

    private Map<UUID, List<Item>> group(TenantId tenant, List<Case> cases, Map<UUID, UUID> managerOf, Map<UUID, Recipient> reach) {
        Map<UUID, List<Item>> byManager = new LinkedHashMap<>();
        for (Case c : cases) {
            UUID manager = managerOf.computeIfAbsent(c.employeeId(), id -> {
                try { return hrFacade.findLinkedEmployee(tenant, id).map(EmployeeSummary::managerId).orElse(null); }
                catch (RuntimeException ex) { return null; }
            });
            if (manager == null) continue;
            if (!reach.containsKey(manager)) {
                Recipient to = null;
                try { to = hrFacade.findLinkedEmployee(tenant, manager).map(TrainingNotifier::managerContact).orElse(null); }
                catch (RuntimeException ex) { log.warn("Manager {} could not be looked up: {}", manager, ex.getMessage()); }
                reach.put(manager, to);
            }
            if (reach.get(manager) != null) byManager.computeIfAbsent(manager, k -> new ArrayList<>()).add(c.item());
        }
        return byManager;
    }

    private void send(TenantId tenant, boolean expired, Map<UUID, List<Item>> byManager, Map<UUID, Recipient> reach) {
        byManager.forEach((manager, items) -> {
            try {
                notificationService.send(NotificationRequest.builder()
                        .tenantId(tenant).type(expired ? NotificationType.TRAINING_CERTIFICATE_EXPIRED : NotificationType.TRAINING_CERTIFICATE_EXPIRING)
                        .recipients(List.of(reach.get(manager)))
                        .title(CertificateDigestRules.title(expired, items.size())).message(CertificateDigestRules.message(expired, items))
                        .actionUrl("/hr/team-training").sourceModule("training").sourceEntityId(manager.toString()).build());
            } catch (RuntimeException ex) {
                log.warn("Certificate message to manager {} could not be sent: {}", manager, ex.getMessage());
            }
        });
    }
}
