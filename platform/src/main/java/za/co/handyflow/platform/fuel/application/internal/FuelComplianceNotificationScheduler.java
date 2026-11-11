package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceItem;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceReminder;
import za.co.handyflow.platform.fuel.domain.repository.FuelComplianceItemRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelComplianceReminderRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Emails (and posts in-app) a warning when a compliance item is 30 days from expiry, 7 days from expiry, and once it has expired.
 * One digest per tenant per run, so a tenant with five renewals due gets one email, not five.
 * Each stage goes out once per expiry date (fuel_compliance_reminders); renewing starts a fresh set.
 * An item first seen late gets only its most urgent stage, and the milder stages are marked done so they never follow it.
 * Items that expired more than {@value #EXPIRED_LOOKBACK_DAYS} days ago are not chased, so switching this on does not flood a tenant with old history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuelComplianceNotificationScheduler {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final int EXPIRED_LOOKBACK_DAYS = 30;
    /** From mildest to most urgent. */
    static final List<String> STAGES = List.of("D30", "D7", "EXPIRED");

    private final FuelComplianceItemRepository items;
    private final FuelComplianceReminderRepository reminders;
    private final NotificationService notifications;
    private final TenantAdminRecipients adminRecipients;

    @Value("${fuel.compliance.reminders.enabled:true}")
    private boolean enabled = true;

    @Scheduled(cron = "0 30 7 * * *", zone = "Africa/Johannesburg")
    public void dailySweep() {
        if (!enabled) return;
        sweep(LocalDate.now(SAST));
    }

    /** Public so a test (or an operator, later) can run it for a given day. Returns how many digests were sent. */
    @Transactional
    public int sweep(LocalDate today) {
        List<FuelComplianceItem> candidates = items.findExpiringBetweenAllTenants(today.minusDays(EXPIRED_LOOKBACK_DAYS), today.plusDays(30));
        if (candidates.isEmpty()) return 0;

        Map<UUID, Set<String>> done = new HashMap<>();
        for (FuelComplianceReminder r : reminders.findByItemIds(candidates.stream().map(FuelComplianceItem::getId).toList())) {
            if (r.getExpiresOn() != null) done.computeIfAbsent(r.getItemId(), k -> new HashSet<>()).add(r.getExpiresOn() + "|" + r.getStage());
        }

        // tenant -> items to tell them about, and the reminders to record once the digest is accepted
        Map<TenantId, List<Due>> byTenant = new LinkedHashMap<>();
        for (FuelComplianceItem i : candidates) {
            String stage = stageFor(i.getExpiresOn(), today);
            if (stage == null) continue;
            Set<String> have = done.getOrDefault(i.getId(), Set.of());
            if (have.contains(i.getExpiresOn() + "|" + stage)) continue;
            byTenant.computeIfAbsent(i.getTenantId(), k -> new ArrayList<>()).add(new Due(i, stage, have));
        }

        int sent = 0;
        for (Map.Entry<TenantId, List<Due>> e : byTenant.entrySet()) {
            List<Recipient> recipients = adminRecipients.resolveTenantAdmins(e.getKey());
            if (recipients.isEmpty()) continue; // not recorded, so the first admin added still hears about it
            List<Due> due = e.getValue();
            boolean anyExpired = due.stream().anyMatch(d -> "EXPIRED".equals(d.stage));
            try {
                notifications.send(NotificationRequest.builder()
                    .tenantId(e.getKey())
                    .type(anyExpired ? NotificationType.FUEL_COMPLIANCE_EXPIRED : NotificationType.FUEL_COMPLIANCE_EXPIRING)
                    .title(anyExpired ? "Fuel compliance: item expired" : "Fuel compliance: renewal due")
                    .message(digest(due, today))
                    .actionUrl("/fuel/compliance")
                    .sourceModule("fuel")
                    .sourceEntityId(due.get(0).item.getId().toString())
                    .recipients(recipients)
                    .build());
            } catch (RuntimeException ex) {
                log.error("Fuel compliance digest failed for tenant {}: {}", e.getKey(), ex.getMessage());
                continue; // nothing recorded, so it is tried again tomorrow
            }
            for (Due d : due) {
                // record this stage and every milder one that was not yet recorded
                for (String s : STAGES.subList(0, STAGES.indexOf(d.stage) + 1)) {
                    if (!d.have.contains(d.item.getExpiresOn() + "|" + s)) reminders.save(FuelComplianceReminder.of(d.item.getTenantId(), d.item.getId(), d.item.getExpiresOn(), s));
                }
            }
            sent++;
        }
        log.info("Fuel compliance reminder sweep: candidates={} digests sent={}", candidates.size(), sent);
        return sent;
    }

    /** The most urgent stage that applies today, or null when nothing is due (more than 30 days away, or expired too long ago to chase). */
    static String stageFor(LocalDate expiresOn, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, expiresOn);
        if (days < 0) return days >= -EXPIRED_LOOKBACK_DAYS ? "EXPIRED" : null;
        if (days <= 7) return "D7";
        if (days <= 30) return "D30";
        return null;
    }

    private static String digest(List<Due> due, LocalDate today) {
        return due.stream()
                .sorted(Comparator.comparing((Due d) -> d.item.getExpiresOn()))
                .map(d -> {
                    long days = ChronoUnit.DAYS.between(today, d.item.getExpiresOn());
                    String when = days < 0 ? "expired " + (-days) + " day" + (days == -1 ? "" : "s") + " ago (" + d.item.getExpiresOn() + ")"
                            : days == 0 ? "expires today" : "expires in " + days + " day" + (days == 1 ? "" : "s") + " (" + d.item.getExpiresOn() + ")";
                    return d.item.getTitle() + " for " + d.item.getSubjectLabel() + " " + when + ".";
                })
                .collect(Collectors.joining(" "))
                + " Renew it in Fuel > Compliance. An expired tanker, driver or company licence blocks load allocation.";
    }

    private record Due(FuelComplianceItem item, String stage, Set<String> have) {}
}
