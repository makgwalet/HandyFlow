package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.ForeignIdentityRules;
import za.co.handyflow.platform.hr.domain.model.ForeignIdentityRules.Expiring;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Warns when a work permit or passport is running out. The company's administrators get one message a day listing everyone whose
 * document is at a reminder day, and each employee with a login hears about their own. Which days are reminder days is decided by
 * {@link ForeignIdentityRules}. Each message goes to a person once a day: the {@link ReminderLedger} stops a second run or a second
 * app instance repeating it. One failing company or employee never stops the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForeignIdentityReminderService {

    private final HrEmployeeRepository employeeRepo;
    private final NotificationService notificationService;
    private final TenantAdminRecipients adminRecipients;
    private final UserRecipientResolver userResolver;
    private final ReminderLedger ledger;

    /** Sends today's reminders for every company. Returns how many messages went out. */
    @Transactional(readOnly = true)
    public int remindAll(LocalDate today) {
        List<HrEmployee> all = employeeRepo.withDocumentsExpiringBy(today.plusDays(ForeignIdentityRules.LOOK_AHEAD_DAYS));
        Map<UUID, List<HrEmployee>> byTenant = all.stream().collect(Collectors.groupingBy(HrEmployee::getTenantId));
        int sent = 0;
        for (Map.Entry<UUID, List<HrEmployee>> e : byTenant.entrySet()) {
            try {
                sent += remindCompany(TenantId.of(e.getKey()), e.getValue(), today);
            } catch (RuntimeException ex) {
                log.error("Work permit reminders failed for tenant {}: {}", e.getKey(), ex.getMessage());
            }
        }
        if (sent > 0) log.info("Work permit and passport reminders: {} message(s)", sent);
        return sent;
    }

    private int remindCompany(TenantId tenant, List<HrEmployee> people, LocalDate today) {
        List<Expiring> due = new ArrayList<>();
        int sent = 0;
        for (HrEmployee p : people) {
            for (Expiring x : dueFor(p, today)) {
                due.add(x);
                sent += tell(tenant, p, x, today);
            }
        }
        if (!due.isEmpty()) {
            List<Recipient> admins = adminRecipients.resolveTenantAdmins(tenant);
            if (!admins.isEmpty()) {
                sent += send(tenant, tenant.getValue().toString(), ForeignIdentityRules.hrTitle(due.size()), ForeignIdentityRules.hrMessage(due, today), admins, today);
            }
        }
        return sent;
    }

    /** The documents of this person that are at a reminder day today. */
    static List<Expiring> dueFor(HrEmployee p, LocalDate today) {
        List<Expiring> out = new ArrayList<>();
        if (ForeignIdentityRules.remindToday(p.getWorkPermitExpiry(), today)) out.add(new Expiring(p.getFullName(), ForeignIdentityRules.PERMIT, p.getWorkPermitExpiry()));
        if (ForeignIdentityRules.remindToday(p.getPassportExpiry(), today)) out.add(new Expiring(p.getFullName(), ForeignIdentityRules.PASSPORT, p.getPassportExpiry()));
        return out;
    }

    /** The employee is told about their own document, when they have a login. */
    private int tell(TenantId tenant, HrEmployee p, Expiring x, LocalDate today) {
        if (p.getUserId() == null) return 0;
        Optional<Recipient> who = userResolver.resolveUser(tenant, p.getUserId());
        if (who.isEmpty()) return 0;
        return send(tenant, p.getId() + ":" + x.kind(), ForeignIdentityRules.employeeTitle(x.kind(), x.on(), today),
                ForeignIdentityRules.employeeMessage(x.kind(), x.on(), today), List.of(who.get()), today);
    }

    private int send(TenantId tenant, String subject, String title, String message, List<Recipient> to, LocalDate today) {
        List<String> claimed = new ArrayList<>();
        List<Recipient> fresh;
        try {
            fresh = ledger.unclaimed(tenant, today, NotificationType.WORK_PERMIT_EXPIRING.name(), subject, title, message, to, claimed);
        } catch (RuntimeException ex) {
            log.warn("Reminder ledger unavailable, sending anyway: {}", ex.getMessage());
            fresh = to;
            claimed.clear();
        }
        if (fresh.isEmpty()) return 0;
        try {
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenant).type(NotificationType.WORK_PERMIT_EXPIRING).title(title).message(message)
                    .actionUrl("/hr").sourceModule("hr").sourceEntityId(subject).recipients(fresh).build());
        } catch (RuntimeException ex) {
            claimed.forEach(d -> ledger.release(tenant, today, d));
            throw ex;
        }
        return 1;
    }
}
