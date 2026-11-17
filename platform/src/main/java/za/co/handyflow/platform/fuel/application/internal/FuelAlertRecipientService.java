package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelAlertRecipient;
import za.co.handyflow.platform.fuel.domain.repository.FuelAlertRecipientRepository;
import za.co.handyflow.platform.fuel.dto.AlertRecipientDtos.Topic;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Who is told about a Fuel alert. Administrators are always told, exactly as before; a company may add named users per topic.
 * A company that adds nobody gets the administrators only, so nothing changes until someone is chosen.
 */
@Service
@RequiredArgsConstructor
public class FuelAlertRecipientService {

    /** Delivery variances: both "to review" and "still unresolved". */
    public static final String VARIANCE = "VARIANCE";
    static final Map<String, String> TOPICS = new LinkedHashMap<>();
    static { TOPICS.put(VARIANCE, "Delivery variances"); }
    static final int MAX_EXTRA = 10;

    private final FuelAlertRecipientRepository repo;
    private final TenantAdminRecipients adminRecipients;

    @Transactional(readOnly = true)
    public List<Topic> list(TenantId tenant) {
        Map<String, List<UUID>> by = new HashMap<>();
        for (FuelAlertRecipient r : repo.findAllForTenant(tenant)) by.computeIfAbsent(r.getTopic(), k -> new ArrayList<>()).add(r.getUserId());
        List<Topic> out = new ArrayList<>();
        TOPICS.forEach((k, label) -> out.add(new Topic(k, label, by.getOrDefault(k, List.of()))));
        return out;
    }

    /** Replaces the extra recipients for a topic. An empty list clears them. Every user must be an active user of this company with an email. */
    @Transactional
    public Topic replace(TenantId tenant, String topic, List<UUID> userIds, UUID by) {
        String label = TOPICS.get(topic == null ? "" : topic);
        if (label == null) throw new IllegalArgumentException("Unknown alert type.");
        Set<UUID> wanted = new LinkedHashSet<>(userIds == null ? List.of() : userIds);
        if (wanted.size() > MAX_EXTRA) throw new IllegalArgumentException("Choose up to " + MAX_EXTRA + " people.");
        if (!wanted.isEmpty()) {
            Set<UUID> found = new HashSet<>();
            for (Recipient r : adminRecipients.resolveUsers(tenant, wanted)) if (r.userId() != null) found.add(r.userId());
            if (!found.containsAll(wanted)) {
                throw new HandyFlowException("Every person chosen must be an active user of this company with an email address.",
                        HttpStatus.UNPROCESSABLE_ENTITY, "FUEL_ALERT_RECIPIENT_INVALID");
            }
        }
        List<FuelAlertRecipient> current = repo.findByTopic(tenant, topic);
        Set<UUID> have = new HashSet<>();
        for (FuelAlertRecipient r : current) {
            if (wanted.contains(r.getUserId())) have.add(r.getUserId());
            else repo.delete(r);
        }
        for (UUID u : wanted) if (!have.contains(u)) repo.save(FuelAlertRecipient.of(tenant, topic, u, by));
        return new Topic(topic, label, new ArrayList<>(wanted));
    }

    /** Administrators plus the company's chosen extras, each person once. Never throws; falls back to the administrators. */
    public List<Recipient> recipientsFor(TenantId tenant, String topic) {
        List<Recipient> admins = adminRecipients.resolveTenantAdmins(tenant);
        List<UUID> extra;
        try {
            extra = repo.findByTopic(tenant, topic).stream().map(FuelAlertRecipient::getUserId).toList();
        } catch (RuntimeException ex) {
            return admins;
        }
        if (extra.isEmpty()) return admins;
        Set<UUID> seen = new HashSet<>();
        List<Recipient> out = new ArrayList<>();
        for (Recipient r : admins) { if (r.userId() == null || seen.add(r.userId())) out.add(r); }
        for (Recipient r : adminRecipients.resolveUsers(tenant, extra)) { if (r.userId() == null || seen.add(r.userId())) out.add(r); }
        return out;
    }
}
