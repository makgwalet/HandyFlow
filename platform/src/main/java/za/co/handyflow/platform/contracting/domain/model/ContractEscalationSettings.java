package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * How this organisation wants waiting approvals followed up. Off until an administrator switches it on; with no row the defaults below apply
 * (24, 48 and 72 hours on a Monday to Friday, 08:00 to 17:00 clock).
 */
@Entity
@Table(name = "contract_escalation_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractEscalationSettings {
    public static final String BUSINESS_HOURS = "BUSINESS_HOURS";
    public static final String CALENDAR = "CALENDAR";

    @Id @Column(name = "tenant_id") private UUID tenantId;
    private boolean enabled;
    @Column(name = "enabled_since") private Instant enabledSince;
    @Column(name = "reminder1_hours") private int reminder1Hours = 24;
    @Column(name = "reminder2_hours") private int reminder2Hours = 48;
    @Column(name = "escalate_hours") private int escalateHours = 72;
    @Column(name = "clock_mode") private String clockMode = BUSINESS_HOURS;
    @Column(name = "work_start") private LocalTime workStart = LocalTime.of(8, 0);
    @Column(name = "work_end") private LocalTime workEnd = LocalTime.of(17, 0);
    @Column(name = "work_days") private String workDays = "MON,TUE,WED,THU,FRI";
    @Column(name = "skip_public_holidays") private boolean skipPublicHolidays = true;
    @Column(name = "escalation_user_ids") private String escalationUserIds = "";
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    public static ContractEscalationSettings create(UUID tenantId) {
        ContractEscalationSettings s = new ContractEscalationSettings();
        s.tenantId = tenantId; s.updatedAt = Instant.now();
        return s;
    }

    public EscalationRules.Thresholds thresholds() { return new EscalationRules.Thresholds(reminder1Hours, reminder2Hours, escalateHours); }
    public Set<DayOfWeek> workDaySet() { return EscalationRules.parseDays(workDays); }
    public List<UUID> escalationUsers() { return EscalationRules.parseIds(escalationUserIds); }
    public boolean isBusinessHours() { return BUSINESS_HOURS.equals(clockMode); }

    /**
     * Saves every setting at once. Throws {@link IllegalArgumentException} (nothing is changed) when they do not make sense together.
     * Switching it on starts the clock from now: approvals already waiting are not chased for the time before this moment.
     */
    public void update(boolean enabled, EscalationRules.Thresholds t, String clockMode, LocalTime start, LocalTime end, Collection<DayOfWeek> days,
                       boolean skipPublicHolidays, Collection<UUID> escalationUsers, UUID by, Instant now) {
        if (!BUSINESS_HOURS.equals(clockMode) && !CALENDAR.equals(clockMode)) throw new IllegalArgumentException("The clock must be BUSINESS_HOURS or CALENDAR");
        if (start == null || end == null || !start.isBefore(end)) throw new IllegalArgumentException("Working hours must start before they end");
        if (days == null || days.isEmpty()) throw new IllegalArgumentException("Choose at least one working day");
        List<UUID> users = EscalationRules.parseIds(EscalationRules.joinIds(escalationUsers));
        if (users.size() > 20) throw new IllegalArgumentException("Name at most 20 people to escalate to");
        boolean switchedOn = enabled && (!this.enabled || this.enabledSince == null);
        this.enabled = enabled;
        if (switchedOn) this.enabledSince = now;
        this.reminder1Hours = t.reminder1Hours(); this.reminder2Hours = t.reminder2Hours(); this.escalateHours = t.escalateHours();
        this.clockMode = clockMode;
        this.workStart = start; this.workEnd = end;
        this.workDays = EscalationRules.joinDays(days);
        this.skipPublicHolidays = skipPublicHolidays;
        this.escalationUserIds = EscalationRules.joinIds(users);
        this.updatedBy = by; this.updatedAt = now;
    }
}
