package za.co.handyflow.platform.invoicing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A company's VAT treatment on a given day: whether it is registered and the standard rate it charges. Registered companies charge a rate above zero;
 * companies that are not registered charge none. With no setting at all a company is treated as registered at the platform rate, which is how every
 * company behaved before the setting existed. Documents keep the rate stored on them, so a change only affects what is created from its date on.
 */
public final class VatRules {
    private VatRules() {}

    public record Row(UUID id, boolean registered, BigDecimal ratePercent, LocalDate effectiveFrom) {}

    /** {@code configured} is false when no setting applies and the default was used. */
    public record Treatment(boolean registered, BigDecimal ratePercent, boolean configured, LocalDate effectiveFrom) {}

    /** The setting in force on {@code date}: the latest change that has started. */
    public static Treatment on(List<Row> rows, LocalDate date, BigDecimal platformRate) {
        Row found = rows.stream().filter(r -> !r.effectiveFrom().isAfter(date)).max(Comparator.comparing(Row::effectiveFrom)).orElse(null);
        if (found == null) return new Treatment(true, platformRate, false, null);
        return new Treatment(found.registered(), found.registered() ? found.ratePercent() : BigDecimal.ZERO.setScale(2), true, found.effectiveFrom());
    }

    /**
     * Checks a new change and returns the rate to store: the given rate (two decimals) for a registered company, zero for one that is not.
     * A change cannot start in the past (documents already made keep their own rate, so a past date would change nothing) or more than a year ahead.
     */
    public static BigDecimal check(boolean registered, BigDecimal rate, LocalDate from, LocalDate today, boolean vatNumberOnFile,
                                   List<Row> existing, BigDecimal platformRate) {
        if (from == null) throw new IllegalArgumentException("Say the date the change starts.");
        if (from.isBefore(today)) throw new IllegalArgumentException("A VAT change cannot start in the past: documents already made keep the rate they were made with.");
        if (from.isAfter(today.plusYears(1))) throw new IllegalArgumentException("A VAT change cannot start more than a year ahead.");
        if (existing.stream().anyMatch(r -> r.effectiveFrom().equals(from)))
            throw new IllegalStateException("There is already a VAT change starting on that date. Cancel it first if you want to replace it.");
        BigDecimal stored;
        if (registered) {
            if (!vatNumberOnFile) throw new IllegalStateException("Add the company's VAT registration number under Company details before switching VAT on.");
            if (rate == null || rate.signum() <= 0 || rate.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("A VAT-registered company charges a rate above 0 and up to 100.");
            stored = rate.setScale(2, RoundingMode.HALF_UP);
        } else {
            stored = BigDecimal.ZERO.setScale(2);
        }
        Treatment before = on(existing, from, platformRate);
        if (before.registered() == registered && before.ratePercent().compareTo(stored) == 0 && before.configured())
            throw new IllegalArgumentException("That is already the VAT treatment from that date.");
        return stored;
    }

    /** Only a change that has not started can be cancelled. */
    public static void checkCancel(LocalDate effectiveFrom, LocalDate today) {
        if (!effectiveFrom.isAfter(today)) throw new IllegalStateException("This VAT change has already started and cannot be cancelled. Make a new change instead.");
    }
}
