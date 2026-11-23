package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** What a site would be invoiced for a month: completed shifts × the contract rate. A preview for a person to check, not an invoice. */
@Service
@RequiredArgsConstructor
public class BillingPreviewService {

    public record Result(UUID siteId, String siteName, String month, LocalDate from, LocalDate to, boolean inProgress, String basis,
                         String currency, boolean excludesVat, List<LineView> lines, double billedHours, double scheduledHours,
                         double actualHours, double unpricedHours, double fixedFeeHours, int completedShiftsWithoutActual,
                         java.util.Map<String, Integer> notBilledShifts, boolean mixedVat, long totalCents, List<String> warnings) {}

    public record LineView(UUID rateId, String type, long rateCents, boolean vatInclusive, double hours, long daysInForce, long amountCents) {}

    /** Optional so a service built by hand still works; without it South Africa's zone is used, never the server's. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.BusinessCalendar calendar;

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Result preview(TenantId tenantId, UUID siteId, String monthAsked, String basisAsked) {
        return preview(tenantId, siteId, monthAsked, basisAsked, Instant.now());
    }

    @Transactional(readOnly = true)
    Result preview(TenantId tenantId, UUID siteId, String monthAsked, String basisAsked, Instant now) {
        UUID tenant = tenantId.getValue();
        BillingRules.Basis basis = BillingRules.Basis.parse(basisAsked);
        var site = jdbc.queryForList("SELECT name FROM security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", siteId, tenant);
        if (site.isEmpty()) throw new HandyFlowException("Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND");
        ZoneId zone = za.co.handyflow.platform.shared.BusinessCalendar.zoneOf(calendar, tenantId);
        YearMonth m = MonthlyPackRules.month(monthAsked, now, zone);
        Instant[] w = MonthlyPackRules.window(m, zone);
        Timestamp fromU = utc(w[0]), toU = utc(w[1]); // shift times are stored without a zone, as UTC

        List<BillingRules.Shift> shifts = jdbc.query("""
                SELECT start_at, end_at, actual_start_at, actual_end_at, status FROM security_shifts
                WHERE tenant_id = ? AND site_id = ? AND deleted_at IS NULL AND start_at < ? AND end_at > ?""",
                (rs, i) -> new BillingRules.Shift(instant(rs.getObject("start_at", LocalDateTime.class)), instant(rs.getObject("end_at", LocalDateTime.class)),
                        instant(rs.getObject("actual_start_at", LocalDateTime.class)), instant(rs.getObject("actual_end_at", LocalDateTime.class)), rs.getString("status")),
                tenant, siteId, toU, fromU);
        // a shift that started the evening before the month is priced by the day it started, so look one day back
        List<BillingRules.RateInfo> rates = jdbc.query("""
                SELECT id, rate_type, amount_cents, vat_inclusive, effective_from, effective_to FROM security_site_rates
                WHERE tenant_id = ? AND site_id = ? AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?)""",
                (rs, i) -> new BillingRules.RateInfo(rs.getObject("id", UUID.class), rs.getString("rate_type"), rs.getLong("amount_cents"), rs.getBoolean("vat_inclusive"),
                        rs.getDate("effective_from").toLocalDate(), rs.getDate("effective_to") == null ? null : rs.getDate("effective_to").toLocalDate()),
                tenant, siteId, java.sql.Date.valueOf(m.atEndOfMonth()), java.sql.Date.valueOf(m.atDay(1).minusDays(1)));

        BillingRules.Preview p = BillingRules.price(m, zone, basis, shifts, rates);
        List<String> warnings = new java.util.ArrayList<>();
        if (rates.isEmpty()) warnings.add("No contract rate covers this month. Add one under the site's rates.");
        if (p.unpricedMinutes() > 0 && !rates.isEmpty()) warnings.add("Some hours fall on days with no contract rate and are not priced.");
        if (MonthlyPackRules.inProgress(m, now, zone)) warnings.add("This month is still in progress; shifts not yet completed are not billed.");
        if (p.mixedVat()) warnings.add("Some rates include VAT and some do not. Check before invoicing.");
        if (basis == BillingRules.Basis.ACTUAL && p.shiftsWithoutActual() > 0) {
            warnings.add(p.shiftsWithoutActual() + " completed shift(s) have no clocked-out time and were counted as scheduled.");
        }
        if (!p.notBilled().isEmpty()) warnings.add("Shifts not billed because they are not completed: " + p.notBilled() + ".");
        warnings.add("VAT is not calculated. Amounts are as set on each rate.");
        return new Result(siteId, (String) site.get(0).get("name"), m.toString(), m.atDay(1), m.atEndOfMonth(), MonthlyPackRules.inProgress(m, now, zone),
                basis.name(), "ZAR", true, p.lines().stream().map(l -> new LineView(l.rateId(), l.type(), l.rateCents(), l.vatInclusive(), hours(l.minutes()), l.days(), l.amountCents())).toList(),
                hours(p.billedMinutes()), hours(p.scheduledMinutes()), hours(p.actualMinutes()), hours(p.unpricedMinutes()), hours(p.fixedFeeMinutes()),
                (int) p.shiftsWithoutActual(), p.notBilled(), p.mixedVat(), p.totalCents(), warnings);
    }

    private static double hours(long minutes) { return Math.round(minutes / 60.0 * 100.0) / 100.0; }
    private static Instant instant(LocalDateTime t) { return t == null ? null : t.toInstant(ZoneOffset.UTC); }
    private static Timestamp utc(Instant i) { return Timestamp.valueOf(LocalDateTime.ofInstant(i, ZoneOffset.UTC)); }
}
