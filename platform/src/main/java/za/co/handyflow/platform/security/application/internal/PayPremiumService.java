package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A company's Sunday and public-holiday pay: the switch and two multipliers, and the days it added to or took off the national list.
 * Payroll reads {@link #context}; nothing here changes pay unless the company has switched the premium on.
 */
@Service
@RequiredArgsConstructor
public class PayPremiumService {

    /** What payroll needs: the settings, and the question "is this date a holiday for this company?". */
    public record Context(PayPremiumRules.Settings settings, PayPremiumRules.Overrides overrides) {
        public Predicate<LocalDate> holidays() { return d -> PayPremiumRules.isHoliday(d, overrides); }
    }

    public record SettingsView(boolean enabled, BigDecimal sundayMultiplier, BigDecimal holidayMultiplier) {}

    public record HolidayView(LocalDate date, String name, String source) {}

    private final JdbcTemplate jdbc;

    public Context context(TenantId tenantId) {
        return new Context(settings(tenantId), overrides(tenantId));
    }

    public PayPremiumRules.Settings settings(TenantId tenantId) {
        List<PayPremiumRules.Settings> rows = jdbc.query(
                "SELECT enabled, sunday_multiplier, holiday_multiplier FROM security_pay_premium_settings WHERE tenant_id = ?",
                (rs, i) -> new PayPremiumRules.Settings(rs.getBoolean(1), rs.getBigDecimal(2), rs.getBigDecimal(3)), tenantId.getValue());
        return rows.isEmpty() ? PayPremiumRules.Settings.OFF : rows.get(0);
    }

    public SettingsView settingsView(TenantId tenantId) {
        PayPremiumRules.Settings s = settings(tenantId);
        return new SettingsView(s.enabled(), s.sundayMultiplier(), s.holidayMultiplier());
    }

    @Transactional
    public SettingsView save(TenantId tenantId, UUID actor, boolean enabled, BigDecimal sunday, BigDecimal holiday) {
        PayPremiumRules.Settings s = PayPremiumRules.validate(enabled, sunday, holiday);
        UUID t = tenantId.getValue();
        jdbc.update("""
                INSERT INTO security_pay_premium_settings (tenant_id, enabled, sunday_multiplier, holiday_multiplier, updated_by, updated_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (tenant_id) DO UPDATE SET enabled = EXCLUDED.enabled, sunday_multiplier = EXCLUDED.sunday_multiplier,
                    holiday_multiplier = EXCLUDED.holiday_multiplier, updated_by = EXCLUDED.updated_by, updated_at = now()""",
                t, s.enabled(), s.sundayMultiplier(), s.holidayMultiplier(), actor);
        audit(t, actor, "PAY_PREMIUM_SET", "{\"enabled\":" + s.enabled() + ",\"sunday\":" + s.sundayMultiplier() + ",\"holiday\":" + s.holidayMultiplier() + "}");
        return new SettingsView(s.enabled(), s.sundayMultiplier(), s.holidayMultiplier());
    }

    public PayPremiumRules.Overrides overrides(TenantId tenantId) {
        Map<LocalDate, String> added = new HashMap<>();
        Set<LocalDate> removed = new HashSet<>();
        jdbc.query("SELECT holiday_date, kind, name FROM security_public_holiday_overrides WHERE tenant_id = ?", rs -> {
            LocalDate d = rs.getDate(1).toLocalDate();
            if ("ADD".equals(rs.getString(2))) added.put(d, rs.getString(3)); else removed.add(d);
        }, tenantId.getValue());
        return new PayPremiumRules.Overrides(added, removed);
    }

    /** The company's holidays in a year, each marked as national, added by the company, or a national day the company took off. */
    public List<HolidayView> holidays(TenantId tenantId, int year) {
        if (year < 2000 || year > 2100) throw new HandyFlowException("Year must be between 2000 and 2100.", HttpStatus.BAD_REQUEST, "PREMIUM_YEAR");
        PayPremiumRules.Overrides o = overrides(tenantId);
        Map<LocalDate, HolidayView> out = new java.util.TreeMap<>();
        SaPublicHolidays.of(year).forEach((d, n) -> out.put(d, new HolidayView(d, n, o.removed().contains(d) ? "REMOVED" : "NATIONAL")));
        o.added().forEach((d, n) -> { if (d.getYear() == year) out.put(d, new HolidayView(d, n, "ADDED")); });
        return List.copyOf(out.values());
    }

    /** Adds a day the company treats as a public holiday. A national day it had taken off is simply put back. */
    @Transactional
    public List<HolidayView> add(TenantId tenantId, UUID actor, LocalDate date, String name) {
        if (date == null) throw new HandyFlowException("Pick a date.", HttpStatus.BAD_REQUEST, "PREMIUM_DATE");
        UUID t = tenantId.getValue();
        PayPremiumRules.Overrides o = overrides(tenantId);
        if (SaPublicHolidays.nameOn(date) != null) {
            if (!o.removed().contains(date)) throw new HandyFlowException("That day is already a public holiday.", HttpStatus.CONFLICT, "PREMIUM_ALREADY");
            clear(t, actor, date, "PUBLIC_HOLIDAY_RESTORED");
        } else {
            String n = name == null ? "" : name.trim();
            if (n.isEmpty() || n.length() > 100) throw new HandyFlowException("Give the day a name of up to 100 characters.", HttpStatus.BAD_REQUEST, "PREMIUM_NAME");
            upsert(t, actor, date, "ADD", n);
        }
        return holidays(tenantId, date.getYear());
    }

    /** Takes a day off the company's holidays: a national day is switched off, a day the company added is deleted. */
    @Transactional
    public List<HolidayView> remove(TenantId tenantId, UUID actor, LocalDate date) {
        if (date == null) throw new HandyFlowException("Pick a date.", HttpStatus.BAD_REQUEST, "PREMIUM_DATE");
        UUID t = tenantId.getValue();
        PayPremiumRules.Overrides o = overrides(tenantId);
        String national = SaPublicHolidays.nameOn(date);
        if (national != null) {
            if (o.removed().contains(date)) throw new HandyFlowException("That day is already taken off.", HttpStatus.CONFLICT, "PREMIUM_ALREADY");
            upsert(t, actor, date, "REMOVE", national);
        } else if (o.added().containsKey(date)) {
            clear(t, actor, date, "PUBLIC_HOLIDAY_CLEARED");
        } else {
            throw new HandyFlowException("That day is not a public holiday.", HttpStatus.NOT_FOUND, "PREMIUM_NOT_FOUND");
        }
        return holidays(tenantId, date.getYear());
    }

    private void upsert(UUID t, UUID actor, LocalDate date, String kind, String name) {
        jdbc.update("""
                INSERT INTO security_public_holiday_overrides (id, tenant_id, holiday_date, kind, name, created_by, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, now())
                ON CONFLICT (tenant_id, holiday_date) DO UPDATE SET kind = EXCLUDED.kind, name = EXCLUDED.name, created_by = EXCLUDED.created_by, created_at = now()""",
                t, date, kind, name, actor);
        audit(t, actor, "PUBLIC_HOLIDAY_" + kind, "{\"date\":\"" + date + "\"}");
    }

    private void clear(UUID t, UUID actor, LocalDate date, String action) {
        jdbc.update("DELETE FROM security_public_holiday_overrides WHERE tenant_id = ? AND holiday_date = ?", t, date);
        audit(t, actor, action, "{\"date\":\"" + date + "\"}");
    }

    private void audit(UUID t, UUID actor, String action, String json) {
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'PAY_PREMIUM', ?, ?, ?::jsonb, now())""", t, actor, t, action, json);
    }
}
