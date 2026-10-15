package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The one place Earthmoving raises alerts. An alert goes to the people the company chose for that kind of alert (or to
 * its administrators when none are chosen), is raised once per cause, waits for someone to acknowledge it, and goes to
 * the next level if nobody does in time. Every send is recorded with its outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthAlertService {

    private final EarthAlertRepository alerts;
    private final EarthAlertRuleRepository rules;
    private final EarthAlertRecipientRepository recipients;
    private final EarthAlertDeliveryRepository deliveries;
    private final NotificationService notificationService;
    private final UserRecipientResolver users;
    private final TenantAdminRecipients tenantAdmins;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    // ── raising ──────────────────────────────────────────────────────────────

    /** Raises an alert unless a live one for the same cause exists. No permission check: called by the system and by services that already checked. */
    @Transactional
    public EarthAlert raise(TenantId tenantId, String type, String severity, UUID assetId, String refType, UUID refId,
                            String title, String message, String dedupeKey, Collection<UUID> alsoTell) {
        EarthmovingRules.oneOf(type, AlertRules.TYPES, "Alert type");
        Optional<EarthAlert> live = alerts.findLiveByKey(tenantId, dedupeKey);
        if (live.isPresent()) return live.get();

        RuleView rule = ruleView(tenantId, type);
        EarthAlert a = EarthAlert.create(tenantId, type, severity, assetId, refType, refId, title, message, dedupeKey);
        alerts.save(a);
        if (!rule.enabled()) {
            // Only a type that may be switched off gets here: the alert is kept for the list but nobody is messaged.
            return a;
        }
        Set<UUID> first = new LinkedHashSet<>(rule.initial());
        if (alsoTell != null) first.addAll(alsoTell);
        send(a, 1, first, rule.initial().isEmpty());
        // An escalation time of 0 means the next level is told at once (critical safety alerts).
        if (rule.escalateAfterMinutes() != null && rule.escalateAfterMinutes() == 0 && !rule.escalation().isEmpty()) {
            a.escalate();
            send(a, 2, new LinkedHashSet<>(rule.escalation()), false);
        }
        trail.record(tenantId, "EARTHMOVING_ALERT_RAISED", "EARTHMOVING_ALERT", a.getId(), null,
                Map.of("type", type, "severity", severity, "title", title));
        return a;
    }

    /** Tells the next level about every alert that has waited too long without being acknowledged. Returns how many. */
    @Transactional
    public int escalateDue(TenantId tenantId, Instant now) {
        int n = 0;
        Map<String, RuleView> byType = new HashMap<>();
        for (EarthAlert a : alerts.findEscalationCandidates(tenantId)) {
            if (!a.canEscalate()) continue;
            RuleView rule = byType.computeIfAbsent(a.getAlertType(), t -> ruleView(tenantId, t));
            if (!rule.enabled() || rule.escalation().isEmpty()) continue;
            if (!AlertRules.isEscalationDue(a.getCreatedAt(), now, rule.escalateAfterMinutes())) continue;
            a.escalate();
            send(a, 2, new LinkedHashSet<>(rule.escalation()), false);
            trail.record(tenantId, "EARTHMOVING_ALERT_ESCALATED", "EARTHMOVING_ALERT", a.getId(), null,
                    Map.of("type", a.getAlertType(), "title", a.getTitle()));
            n++;
        }
        return n;
    }

    // ── people responding ────────────────────────────────────────────────────

    @Transactional
    public AlertResponse acknowledge(TenantId tenantId, UUID id, UUID actor) {
        access.require(EarthmovingRules.WRITE, "acknowledge an alert");
        EarthAlert a = find(tenantId, id);
        a.acknowledge(actor);
        trail.record(tenantId, "EARTHMOVING_ALERT_ACKNOWLEDGED", "EARTHMOVING_ALERT", id, Map.of("status", "OPEN"), Map.of("status", "ACKNOWLEDGED"));
        return toResponse(a, deliveries.findForAlerts(tenantId, List.of(id)));
    }

    @Transactional
    public AlertResponse resolve(TenantId tenantId, UUID id, String note, UUID actor) {
        access.require(EarthmovingRules.MAINTAIN, "resolve an alert");
        String why = EarthmovingRules.requireReason(note, "resolving an alert");
        EarthAlert a = find(tenantId, id);
        a.resolve(actor, why);
        trail.record(tenantId, "EARTHMOVING_ALERT_RESOLVED", "EARTHMOVING_ALERT", id, Map.of("status", "OPEN"), Map.of("status", "RESOLVED", "note", why));
        return toResponse(a, deliveries.findForAlerts(tenantId, List.of(id)));
    }

    /** Closes the live alert for a cause that has been dealt with (machine released, job completed). No-op if there is none. */
    @Transactional
    public void resolveByKey(TenantId tenantId, String dedupeKey, UUID actor, String note) {
        alerts.findLiveByKey(tenantId, dedupeKey).ifPresent(a -> {
            a.resolve(actor, note);
            trail.record(tenantId, "EARTHMOVING_ALERT_RESOLVED", "EARTHMOVING_ALERT", a.getId(), null, Map.of("status", "RESOLVED", "note", note));
        });
    }

    // ── reading ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AlertResponse> list(TenantId tenantId, boolean openOnly, Pageable pageable) {
        access.require(EarthmovingRules.READ, "view alerts");
        Page<EarthAlert> page = alerts.findForTenant(tenantId, openOnly, pageable);
        Map<UUID, List<EarthAlertDelivery>> byAlert = deliveries
                .findForAlerts(tenantId, page.getContent().stream().map(EarthAlert::getId).toList()).stream()
                .collect(Collectors.groupingBy(EarthAlertDelivery::getAlertId));
        return page.map(a -> toResponse(a, byAlert.getOrDefault(a.getId(), List.of())));
    }

    // ── rules ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listRules(TenantId tenantId) {
        access.require(EarthmovingRules.ADMIN, "view alert rules");
        return AlertRules.TYPES.stream().sorted().map(t -> toRuleResponse(t, ruleView(tenantId, t))).toList();
    }

    @Transactional
    public AlertRuleResponse saveRule(TenantId tenantId, String type, SaveAlertRuleRequest req) {
        access.require(EarthmovingRules.ADMIN, "change alert rules");
        String t = type == null ? null : type.trim().toUpperCase();
        boolean enabled = req.enabled() == null || req.enabled();
        List<UUID> initial = distinct(req.initialUserIds());
        List<UUID> escalation = distinct(req.escalationUserIds());
        AlertRules.checkRule(t, enabled, req.escalateAfterMinutes(), initial, escalation);
        for (UUID u : concat(initial, escalation)) {
            if (users.resolveUser(tenantId, u).isEmpty())
                throw new IllegalArgumentException("One of the people chosen is not a user of this company.");
        }
        RuleView before = ruleView(tenantId, t);
        EarthAlertRule rule = rules.findByType(tenantId, t).orElseGet(() -> rules.save(EarthAlertRule.create(tenantId, t)));
        rule.configure(enabled, req.escalateAfterMinutes());
        recipients.deleteForRule(tenantId, rule.getId());
        recipients.flush();
        initial.forEach(u -> recipients.save(EarthAlertRecipient.of(tenantId, rule.getId(), u, 1)));
        escalation.forEach(u -> recipients.save(EarthAlertRecipient.of(tenantId, rule.getId(), u, 2)));
        trail.record(tenantId, "EARTHMOVING_ALERT_RULE_CHANGED", "EARTHMOVING_ALERT_RULE", rule.getId(),
                Map.of("type", t, "enabled", String.valueOf(before.enabled()), "initial", String.valueOf(before.initial().size()),
                        "escalation", String.valueOf(before.escalation().size())),
                Map.of("type", t, "enabled", String.valueOf(enabled), "initial", String.valueOf(initial.size()),
                        "escalation", String.valueOf(escalation.size())));
        return toRuleResponse(t, new RuleView(enabled, req.escalateAfterMinutes(), initial, escalation));
    }

    // ── internals ────────────────────────────────────────────────────────────

    record RuleView(boolean enabled, Integer escalateAfterMinutes, List<UUID> initial, List<UUID> escalation) {}

    RuleView ruleView(TenantId tenantId, String type) {
        Optional<EarthAlertRule> r = rules.findByType(tenantId, type);
        if (r.isEmpty()) return new RuleView(true, null, List.of(), List.of());
        List<EarthAlertRecipient> rs = recipients.findForRule(tenantId, r.get().getId());
        return new RuleView(r.get().isEnabled(), r.get().getEscalateAfterMinutes(),
                rs.stream().filter(x -> x.getLevel() == 1).map(EarthAlertRecipient::getUserId).toList(),
                rs.stream().filter(x -> x.getLevel() == 2).map(EarthAlertRecipient::getUserId).toList());
    }

    /** Sends to the chosen people; when none were chosen at this level, to the company's administrators. Every attempt is recorded. */
    private void send(EarthAlert a, int level, Set<UUID> userIds, boolean adminFallback) {
        TenantId tenantId = a.getTenantId();
        List<Recipient> to = new ArrayList<>();
        List<EarthAlertDelivery> records = new ArrayList<>();
        for (UUID u : userIds) {
            Optional<Recipient> r = users.resolveUser(tenantId, u);
            if (r.isPresent()) to.add(r.get());
            else records.add(EarthAlertDelivery.of(tenantId, a.getId(), u, level, false, "User not found or inactive"));
        }
        String via = null;
        if (to.isEmpty()) {
            to.addAll(tenantAdmins.resolveTenantAdmins(tenantId));
            via = adminFallback ? "Sent to company administrators because no recipient is set up"
                    : "Sent to company administrators because none of the chosen people could be reached";
        }
        if (to.isEmpty()) {
            records.add(EarthAlertDelivery.of(tenantId, a.getId(), null, level, false, "Nobody to notify"));
        } else {
            boolean ok = true;
            String failure = null;
            try {
                notificationService.send(NotificationRequest.builder()
                        .tenantId(tenantId)
                        .type("BREAKDOWN".equals(a.getAlertType()) ? NotificationType.ASSET_BREAKDOWN : NotificationType.ASSET_SERVICE_DUE)
                        .severity("CRITICAL".equals(a.getSeverity()) ? NotificationSeverity.CRITICAL : null)
                        .channels("CRITICAL".equals(a.getSeverity())
                                ? Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.SMS) : null)
                        .title((level == 2 ? "ESCALATED: " : "") + a.getTitle())
                        .message(a.getMessage())
                        .actionUrl("/earthmoving/alerts")
                        .sourceModule("earthmoving")
                        .sourceEntityId(a.getId().toString())
                        .recipients(to)
                        .build());
            } catch (Exception e) {
                ok = false;
                failure = "Sending failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                log.error("[Earthmoving] Alert {} could not be sent: {}", a.getId(), e.getMessage(), e);
            }
            for (Recipient r : to) records.add(EarthAlertDelivery.of(tenantId, a.getId(), r.userId(), level, ok, ok ? via : failure));
        }
        deliveries.saveAll(records);
    }

    private EarthAlert find(TenantId tenantId, UUID id) {
        return alerts.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Alert", id.toString()));
    }

    private static List<UUID> distinct(List<UUID> in) { return in == null ? List.of() : in.stream().filter(Objects::nonNull).distinct().toList(); }

    private static List<UUID> concat(List<UUID> a, List<UUID> b) { List<UUID> l = new ArrayList<>(a); l.addAll(b); return l; }

    private AlertRuleResponse toRuleResponse(String type, RuleView v) {
        return new AlertRuleResponse(type, v.enabled(), v.escalateAfterMinutes(), v.initial(), v.escalation(),
                v.initial().isEmpty(), !"BREAKDOWN".equals(type));
    }

    private AlertResponse toResponse(EarthAlert a, List<EarthAlertDelivery> ds) {
        return new AlertResponse(a.getId(), a.getAlertType(), a.getSeverity(), a.getAssetId(), a.getRefType(), a.getRefId(), a.getTitle(),
                a.getMessage(), a.getStatus(), a.getLevel(), a.getCreatedAt(), a.getEscalatedAt(), a.getAcknowledgedBy(),
                a.getAcknowledgedAt(), a.getResolvedAt(), a.getResolutionNote(),
                ds.stream().map(d -> new AlertDeliveryResponse(d.getUserId(), d.getLevel(), d.getStatus(), d.getDetail(), d.getSentAt())).toList());
    }
}
