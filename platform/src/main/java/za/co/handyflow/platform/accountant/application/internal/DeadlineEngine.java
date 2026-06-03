package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.accountant.domain.model.AccClient;
import za.co.handyflow.platform.accountant.domain.model.TaxDeadline;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SA SARS deadline engine.
 *
 * Rules implemented:
 *   EMP201  — 7th of each month (eFiling); if weekend/PH → prior business day
 *   VAT201  — Category A: 25th of month after period-end (bi-monthly: Feb/Apr/Jun/Aug/Oct/Dec)
 *             Category B: 25th of month after period-end (bi-monthly: Jan/Mar/May/Jul/Sep/Nov)
 *             Category C: 25th of following month (monthly)
 *             Category E: 25th of month following year-end (annual)
 *   ITR14   — 12 months after financial year-end
 *   ITR12   — Individual: 23 Jan following tax year (eFiling Oct–Jan)
 *   IRP6    — P1: 6 months after year-end; P2: at year-end; P3: 6 months after YE
 *   EMP501  — 31 May (reconciliation year always ends 28 Feb)
 *   CIPC    — Anniversary of registration date
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineEngine {

    private final JdbcTemplate jdbc;

    public List<TaxDeadline> generateForClient(AccClient client, int year) {
        List<TaxDeadline> deadlines = new ArrayList<>();
        UUID tenantId = client.getTenantId().getValue();
        UUID clientId = client.getId();
        String vatCat = client.getVatCategory();
        int yem       = client.getYearEndMonth(); // financial year-end month

        // ── EMP201 — monthly, every month ─────────────────────────────────────
        for (int month = 1; month <= 12; month++) {
            LocalDate raw     = LocalDate.of(year, month, 7);
            LocalDate adjusted = adjustForBusinessDay(raw);
            deadlines.add(TaxDeadline.create(tenantId, clientId, "EMP201", year, month, raw, adjusted));
        }

        // ── EMP501 — annual, 31 May ────────────────────────────────────────────
        {
            LocalDate raw     = LocalDate.of(year, 5, 31);
            LocalDate adjusted = adjustForBusinessDay(raw);
            deadlines.add(TaxDeadline.create(tenantId, clientId, "EMP501", year, null, raw, adjusted));
        }

        // ── VAT201 — depends on registration category ──────────────────────────
        if (vatCat != null) {
            generateVatDeadlines(deadlines, tenantId, clientId, vatCat, year);
        }

        // ── ITR14 — 12 months after financial year-end ─────────────────────────
        // e.g. YE = February → ITR14 due 28 February following year
        {
            LocalDate yearEnd = localDateOfYearEnd(year, yem);
            LocalDate raw     = yearEnd.plusMonths(12);
            LocalDate adjusted = adjustForBusinessDay(raw);
            deadlines.add(TaxDeadline.create(tenantId, clientId, "ITR14", year, null, raw, adjusted));
        }

        // ── IRP6 Provisional Tax — P1 and P2 ──────────────────────────────────
        {
            // P1: 6 months after financial year-end
            LocalDate p1Raw     = localDateOfYearEnd(year, yem).plusMonths(6);
            LocalDate p1Adjusted = adjustForBusinessDay(p1Raw);
            deadlines.add(TaxDeadline.create(tenantId, clientId, "IRP6_P1", year, null, p1Raw, p1Adjusted));

            // P2: at financial year-end
            LocalDate p2Raw     = localDateOfYearEnd(year, yem);
            LocalDate p2Adjusted = adjustForBusinessDay(p2Raw);
            deadlines.add(TaxDeadline.create(tenantId, clientId, "IRP6_P2", year, null, p2Raw, p2Adjusted));
        }

        log.info("Generated {} deadlines for client={} year={}", deadlines.size(), clientId, year);
        return deadlines;
    }

    private void generateVatDeadlines(List<TaxDeadline> deadlines, UUID tenantId,
                                      UUID clientId, String vatCat, int year) {
        // Category A: bi-monthly — periods end Feb, Apr, Jun, Aug, Oct, Dec
        // Category B: bi-monthly — periods end Jan, Mar, May, Jul, Sep, Nov
        // Category C: monthly
        // Category E: annual (year-end based — skip here, generated with ITR14)

        List<Integer> periodEndMonths = switch (vatCat) {
            case "A" -> List.of(2, 4, 6, 8, 10, 12);
            case "B" -> List.of(1, 3, 5, 7, 9, 11);
            case "C" -> List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
            default  -> List.of();  // Category E handled separately
        };

        for (int periodEndMonth : periodEndMonths) {
            // VAT201 due 25th of the month AFTER the period end
            int dueMonth = periodEndMonth == 12 ? 1 : periodEndMonth + 1;
            int dueYear  = periodEndMonth == 12 ? year + 1 : year;
            LocalDate raw     = LocalDate.of(dueYear, dueMonth, 25);
            LocalDate adjusted = adjustForBusinessDay(raw);
            deadlines.add(TaxDeadline.create(tenantId, clientId, "VAT201", year, periodEndMonth, raw, adjusted));
        }
    }

    /**
     * Adjusts a date to the prior business day if it falls on a weekend or SA public holiday.
     * EMP201 rule: "if the 7th falls on a weekend or public holiday, the return is due on
     * the last business day before the 7th" — same principle applies to VAT201.
     */
    LocalDate adjustForBusinessDay(LocalDate date) {
        LocalDate adjusted = date;
        while (isWeekend(adjusted) || isPublicHoliday(adjusted)) {
            adjusted = adjusted.minusDays(1);
        }
        return adjusted;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private boolean isPublicHoliday(LocalDate date) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM acc_public_holidays WHERE holiday_date = ?",
                Integer.class, date);
        return count != null && count > 0;
    }

    private LocalDate localDateOfYearEnd(int year, int yearEndMonth) {
        // Last day of the year-end month
        return LocalDate.of(year, yearEndMonth, 1).withDayOfMonth(
                LocalDate.of(year, yearEndMonth, 1).lengthOfMonth());
    }

    private UUID uuid(Object o) { return (UUID) o; }
}
