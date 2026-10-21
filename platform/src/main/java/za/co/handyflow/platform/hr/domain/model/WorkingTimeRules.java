package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The Basic Conditions of Employment Act limits that attendance and overtime are checked against, as they stood from a date.
 * <p>
 * Versioned in code, not hard-coded at the point of use: the earnings threshold changes about once a year, so a new row is added
 * with its date and source and old periods keep the values they had. A date before the first row has no rules and is refused,
 * rather than guessed at.
 * <p>
 * The percentages and hours are from the Act (ss 9, 10, 14, 15, 16, 35); the threshold amounts are from published law-firm
 * summaries of the Minister's determinations (Cliffe Dekker Hofmeyr, 20 April 2026), not from the Government Gazette itself.
 */
public final class WorkingTimeRules {

    private WorkingTimeRules() {}

    /**
     * @param effectiveFrom      first day these values apply
     * @param earningsThreshold  annual earnings above which the hours-of-work and overtime provisions no longer apply (s 6(3))
     * @param weeklyOrdinaryMax  ordinary hours per week (s 9)
     * @param weeklyOvertimeMax  overtime hours per week (s 10)
     * @param dailyMax           ordinary plus overtime hours in a day (s 10)
     * @param overtimeMultiplier overtime and Sunday work for those who ordinarily work Sundays (ss 10, 16)
     * @param sundayMultiplier   Sunday work for those who do not ordinarily work Sundays (s 16)
     * @param dailyRestHours     consecutive hours of rest between working days (s 15)
     * @param mealAfterHours     continuous hours of work after which a meal interval is due (s 14)
     */
    public record Values(LocalDate effectiveFrom, BigDecimal earningsThreshold, int weeklyOrdinaryMax, int weeklyOvertimeMax, int dailyMax,
                         BigDecimal overtimeMultiplier, BigDecimal sundayMultiplier, int dailyRestHours, int mealAfterHours, String source) {}

    public static final List<Values> ALL = List.of(
            new Values(LocalDate.of(2025, 4, 1), new BigDecimal("261748.45"), 45, 10, 12, new BigDecimal("1.5"), new BigDecimal("2.0"), 12, 5,
                    "BCEA earnings threshold from 1 April 2025 (as stated by Cliffe Dekker Hofmeyr, 20 April 2026)"),
            new Values(LocalDate.of(2026, 5, 1), new BigDecimal("269600.90"), 45, 10, 12, new BigDecimal("1.5"), new BigDecimal("2.0"), 12, 5,
                    "BCEA earnings threshold from 1 May 2026 (Cliffe Dekker Hofmeyr, 20 April 2026; Bowmans; DLA Piper)"));

    /** The values in force on a date. */
    public static Values on(LocalDate date) {
        Values found = null;
        for (Values v : ALL) if (!v.effectiveFrom().isAfter(date)) found = v;
        if (found == null) throw new IllegalStateException("There are no working-time rules for " + date + ". The earliest are from " + ALL.get(0).effectiveFrom() + ".");
        return found;
    }

    /** True when the employee earns more than the threshold, so the Act's overtime rates and limits do not apply automatically. */
    public static boolean aboveThreshold(BigDecimal monthlyGross, Values v) {
        return monthlyGross != null && monthlyGross.multiply(BigDecimal.valueOf(12)).compareTo(v.earningsThreshold()) > 0;
    }
}
