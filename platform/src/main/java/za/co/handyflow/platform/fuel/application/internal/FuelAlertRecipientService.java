package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelAlertRecipient;
import za.co.handyflow.platform.fuel.domain.model.FuelAlertTopicSetting;
import za.co.handyflow.platform.fuel.domain.repository.FuelAlertTopicSettingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelAlertRecipientRepository;
import za.co.handyflow.platform.fuel.dto.AlertRecipientDtos.Topic;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Who is told about a Fuel alert. Administrators are told, exactly as before, and a company may add named users per topic.
 * A company may leave administrators off the routine notification, but only while at least one other person is told; if nobody
 * else can be reached the administrators are told anyway, so an alert never goes unseen. Excluding an administrator only affects
 * this notification: they still see the alert where it is shown in the app, and mandatory escalations do not use this list.
 */
@Service
@RequiredArgsConstructor
public class FuelAlertRecipientService {

    /** Delivery variances: both "to review" and "still unresolved". */
    public static final String VARIANCE = "VARIANCE";
    static final Map<String, String> TOPICS = new LinkedHashMap<>();
    /** A routine notice for each shift accepted automatically. Administrators are not told by default: they get exceptions and escalations, not every routine acceptance. */
    public static final String SHIFT_ACCEPTED = "SHIFT_ACCEPTED";
    /** A shift that could not be accepted automatically, or closed outside its tolerance, and needs a person. */
    public static final String SHIFT_EXCEPTION = "SHIFT_EXCEPTION";
    /** The same attendant closing shifts outside tolerance again and again, as the company defines it. */
    public static final String SHIFT_REPEAT = "SHIFT_REPEAT";
    /** A driver's arrival check was blocked. The driver is always told; these people are told as well. */
    public static final String ARRIVAL_BLOCKED = "ARRIVAL_BLOCKED";
    /** A delivery is still open well past its time. Dispatch escalation: told after the driver, if it is still open. */
    public static final String DELIVERY_OVERDUE = "DELIVERY_OVERDUE";
    static {
        TOPICS.put(ARRIVAL_BLOCKED, "Blocked arrival checks");
        TOPICS.put(DELIVERY_OVERDUE, "Overdue deliveries (dispatch)");
        TOPICS.put(VARIANCE, "Delivery variances");
        TOPICS.put(SHIFT_ACCEPTED, "Shifts accepted automatically");
        TOPICS.put(SHIFT_EXCEPTION, "Shifts that need review");
        TOPICS.put(SHIFT_REPEAT, "Repeated shift variances");
    }
    /** Topics where administrators are told unless the company leaves them off. The others go only to the people chosen. */
    static final Set<String> ADMINS_BY_DEFAULT = Set.of(VARIANCE, SHIFT_EXCEPTION, SHIFT_REPEAT, ARRIVAL_BLOCKED, DELIVERY_OVERDUE);
    static final int MAX_EXTRA = 10;

    private final FuelAlertRecipientRepository repo;
    private final TenantAdminRecipients adminRecipients;
    private final FuelAlertTopicSettingRepository settings;

    @Transactional(readOnly = true)
    public List<Topic> list(TenantId tenant) {
        Map<String, Boolean> excl = new HashMap<>();
        for (FuelAlertTopicSetting st : settings.findAllForTenant(tenant.getValue())) excl.put(st.getTopic(), st.isExcludeAdmins());
        Map<String, List<UUID>> by = new HashMap<>();
        for (FuelAlertRecipient r : repo.findAllForTenant(tenant)) by.computeIfAbsent(r.getTopic(), k -> new ArrayList<>()).add(r.getUserId());
        List<Topic> out = new ArrayList<>();
        TOPICS.forEach((k, label) -> out.add(new Topic(k, label, by.getOrDefault(k, List.of()), excl.getOrDefault(k, false), ADMINS_BY_DEFAULT.contains(k))));
        return out;
    }

    /** Replaces the extra recipients for a topic. An empty list clears them. Every user must be an active user of this company with an email. */
    @Transactional
    public Topic replace(TenantId tenant, String topic, List<UUID> userIds, UUID by) {
        return replace(tenant, topic, userIds, null, by);
    }

    /** excludeAdmins null keeps the current setting. Excluding administrators needs at least one other person chosen. */
    @Transactional
    public Topic replace(TenantId tenant, String topic, List<UUID> userIds, Boolean excludeAdmins, UUID by) {
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
        FuelAlertTopicSetting setting = settings.findOne(tenant.getValue(), topic).orElse(null);
        boolean exclude = excludeAdmins != null ? excludeAdmins : setting != null && setting.isExcludeAdmins();
        if (exclude && wanted.isEmpty()) {
            if (excludeAdmins != null && excludeAdmins) throw new IllegalArgumentException("Choose at least one other person before leaving administrators off, so the alert is always seen.");
            exclude = false;   // the others were all removed: administrators go back on
        }
        if (setting == null) { if (exclude) settings.save(FuelAlertTopicSetting.of(tenant, topic, true, by)); }
        else if (setting.isExcludeAdmins() != exclude) setting.set(exclude, by);
        List<FuelAlertRecipient> current = repo.findByTopic(tenant, topic);
        Set<UUID> have = new HashSet<>();
        for (FuelAlertRecipient r : current) {
            if (wanted.contains(r.getUserId())) have.add(r.getUserId());
            else repo.delete(r);
        }
        for (UUID u : wanted) if (!have.contains(u)) repo.save(FuelAlertRecipient.of(tenant, topic, u, by));
        return new Topic(topic, label, new ArrayList<>(wanted), exclude, ADMINS_BY_DEFAULT.contains(topic));
    }

    /**
     * Who to tell. Administrators plus the company's chosen people for most topics; only the chosen people for a routine topic. Each person once.
     * Never throws; falls back to the administrators for a topic they are normally told about.
     */
    public List<Recipient> recipientsFor(TenantId tenant, String topic) {
        boolean adminsNormally = ADMINS_BY_DEFAULT.contains(topic);
        List<Recipient> admins = adminsNormally ? adminRecipients.resolveTenantAdmins(tenant) : List.of();
        List<UUID> extra;
        boolean exclude;
        try {
            extra = repo.findByTopic(tenant, topic).stream().map(FuelAlertRecipient::getUserId).toList();
            exclude = adminsNormally && !extra.isEmpty() && settings.findOne(tenant.getValue(), topic).map(FuelAlertTopicSetting::isExcludeAdmins).orElse(false);
        } catch (RuntimeException ex) {
            return admins;
        }
        if (extra.isEmpty()) return admins;
        List<Recipient> chosen;
        try {
            chosen = adminRecipients.resolveUsers(tenant, extra);
        } catch (RuntimeException ex) {
            return admins;
        }
        Set<UUID> seen = new HashSet<>();
        List<Recipient> out = new ArrayList<>();
        if (!exclude || chosen.isEmpty()) {
            for (Recipient r : admins) { if (r.userId() == null || seen.add(r.userId())) out.add(r); }
        }
        for (Recipient r : chosen) { if (r.userId() == null || seen.add(r.userId())) out.add(r); }
        return out;
    }
}
