package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.Criticality;
import za.co.handyflow.platform.legalcompliance.domain.model.RiskLevel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure date and cadence rules for PAIA requests, regulator filings, FICA reviews and third-party certificates. No framework dependencies.
 *
 * The statutory figures are defaults that the organisation's legal adviser should confirm before relying on them: PAIA s25 asks a private body to decide
 * a request within 30 days, and s26 allows one extension of up to a further 30 days. They are counted here in calendar days. A request not decided
 * by its (extended) due date is flagged "deemed refusal risk": PAIA treats a failure to decide as a refusal (s27), which the requester may challenge.
 * The FICA review cadences are the platform's defaults for a risk-based approach; the organisation's own RMCP governs and may differ.
 */
public final class PrivacyPartyRules {

    private PrivacyPartyRules() {}

    public static final int PAIA_DECISION_DAYS = 30;
    public static final int PAIA_MAX_EXTENSION_DAYS = 30;
    public static final int PAIA_WARNING_DAYS = 5;
    public static final int FILING_WARNING_DAYS = 30;
    public static final int EXPIRY_WARNING_DAYS = 30;

    // ── PAIA ──
    public static LocalDate paiaDueDate(LocalDate received) { return received.plusDays(PAIA_DECISION_DAYS); }

    public static LocalDate paiaMaxExtendedDate(LocalDate due) { return due.plusDays(PAIA_MAX_EXTENSION_DAYS); }

    public static LocalDate paiaEffectiveDue(LocalDate due, LocalDate extendedUntil) { return extendedUntil != null && extendedUntil.isAfter(due) ? extendedUntil : due; }

    /** True once the (extended) due date has passed with no decision recorded. */
    public static boolean paiaOverdue(LocalDate effectiveDue, boolean decided, LocalDate today) { return !decided && today.isAfter(effectiveDue); }

    public static boolean paiaDueSoon(LocalDate effectiveDue, boolean decided, LocalDate today) {
        return !decided && !today.isAfter(effectiveDue) && ChronoUnit.DAYS.between(today, effectiveDue) <= PAIA_WARNING_DAYS;
    }

    // ── Filings ──
    public static boolean filingOverdue(LocalDate due, boolean submitted, LocalDate today) { return !submitted && today.isAfter(due); }

    public static boolean filingDueSoon(LocalDate due, boolean submitted, LocalDate today) {
        return !submitted && !today.isAfter(due) && ChronoUnit.DAYS.between(today, due) <= FILING_WARNING_DAYS;
    }

    // ── FICA ──
    /** Default review cadence by risk rating, in months. A politically exposed person is always treated as high risk. */
    public static int ficaReviewMonths(RiskLevel risk, boolean pep) {
        if (pep || risk == RiskLevel.HIGH) return 6;
        return risk == RiskLevel.MEDIUM ? 12 : 24;
    }

    public static LocalDate ficaNextReview(LocalDate from, RiskLevel risk, boolean pep) { return from.plusMonths(ficaReviewMonths(risk, pep)); }

    // ── Third parties ──
    public static int defaultReviewMonths(Criticality c) {
        return switch (c) { case CRITICAL -> 6; case HIGH -> 12; case MEDIUM -> 18; case LOW -> 24; };
    }

    public enum Expiry { NO_EXPIRY, VALID, EXPIRING, EXPIRED }

    public static Expiry expiry(LocalDate validTo, LocalDate today) {
        if (validTo == null) return Expiry.NO_EXPIRY;
        if (validTo.isBefore(today)) return Expiry.EXPIRED;
        return ChronoUnit.DAYS.between(today, validTo) <= EXPIRY_WARNING_DAYS ? Expiry.EXPIRING : Expiry.VALID;
    }

    public static boolean reviewOverdue(LocalDate nextReview, LocalDate today) { return nextReview != null && today.isAfter(nextReview); }
}
