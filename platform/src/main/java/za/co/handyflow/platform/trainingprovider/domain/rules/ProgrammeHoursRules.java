package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Optional workplace hours on a programme version. A programme need not have any, and none are assumed. Hours are recorded only with a
 * source note saying where the number comes from and who checked it. This holds no default hours and no pass mark: logged hours are
 * reported next to the recorded figure, never compared to it as a pass.
 */
public final class ProgrammeHoursRules {

    public static final Set<String> SOURCE_TYPES = Set.of("QUALIFICATION_DOCUMENT", "CURRICULUM", "AUTHORITY_LETTER", "ACCREDITATION_DOCUMENT", "FUNDER_AGREEMENT",
            "PROVIDER_POLICY", "OTHER");
    public static final BigDecimal MAX_HOURS = BigDecimal.valueOf(20000);
    public static final int MIN_REASON = 10;

    private ProgrammeHoursRules() {
    }

    /** The source note and figure as entered. */
    public record Entry(BigDecimal hours, String sourceType, String sourceReference, LocalDate sourceVersionDate, String sourceSection, LocalDate effectiveDate,
                        String verifiedBy, LocalDate verifiedOn) {
    }

    public static String problem(Entry e, LocalDate today) {
        if (e.hours() == null || e.hours().signum() <= 0 || e.hours().compareTo(MAX_HOURS) > 0) {
            return "Hours must be more than 0 and no more than " + MAX_HOURS.toPlainString();
        }
        if (e.hours().scale() > 2 && e.hours().stripTrailingZeros().scale() > 2) {
            return "Hours can have at most two decimal places";
        }
        if (e.sourceType() == null || !SOURCE_TYPES.contains(e.sourceType())) {
            return "Say what kind of source the hours come from";
        }
        if (len(e.sourceReference()) < 3) {
            return "Give the document or reference the hours come from";
        }
        if (len(e.sourceSection()) < 1) {
            return "Give the section or page where the hours are stated";
        }
        if (e.effectiveDate() == null) {
            return "Give the date the source says the hours take effect";
        }
        if (len(e.verifiedBy()) < 2) {
            return "Say who checked the source";
        }
        if (e.verifiedOn() == null) {
            return "Give the day it was checked";
        }
        if (e.verifiedOn().isAfter(today)) {
            return "The check cannot be dated in the future";
        }
        if (e.sourceVersionDate() != null && e.sourceVersionDate().isAfter(e.verifiedOn())) {
            return "The source's own date cannot be after the day it was checked";
        }
        return null;
    }

    public static String withdrawProblem(String status, String reason) {
        if ("WITHDRAWN".equals(status)) {
            return "These hours are already withdrawn";
        }
        return reason == null || reason.trim().length() < MIN_REASON ? "Give a reason of at least " + MIN_REASON + " characters" : null;
    }

    /** Plain wording kept next to logged hours. Never a pass, never "met". */
    public static String note(BigDecimal recorded, BigDecimal logged, BigDecimal confirmed) {
        return "This programme version records " + plain(recorded) + " workplace hours. On this placement " + plain(logged) + " are logged, "
                + plain(confirmed) + " of them confirmed by the mentor. Logged hours are a record of time spent. They are not a competency decision.";
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static int len(String s) {
        return s == null ? 0 : s.trim().length();
    }
}
