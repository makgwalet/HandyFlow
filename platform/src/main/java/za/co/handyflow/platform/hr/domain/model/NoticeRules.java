package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Notice when employment ends (BCEA section 37): one week up to six months of service, two weeks from six months to a year, four weeks
 * from a year. Pay in place of notice is owed (section 38) when the employer ends employment and gives less notice than that. Pure.
 * Farm and domestic workers have four weeks after six months; that is not applied here and the note says so.
 */
public final class NoticeRules {

    private NoticeRules() {}

    public static final String RESIGNATION = "RESIGNATION", EMPLOYER_NOTICE = "EMPLOYER_NOTICE", SUMMARY_DISMISSAL = "SUMMARY_DISMISSAL",
            CONTRACT_END = "CONTRACT_END", MUTUAL = "MUTUAL", OTHER = "OTHER";
    public static final Set<String> TYPES = Set.of(RESIGNATION, EMPLOYER_NOTICE, SUMMARY_DISMISSAL, CONTRACT_END, MUTUAL, OTHER);

    /** Null when the answers can be saved. Both are optional; a notice date needs a type to mean anything. */
    public static String problem(String endType, LocalDate noticeGivenOn, LocalDate lastDay, LocalDate start) {
        if (endType != null && !TYPES.contains(endType)) return "Choose how employment ended.";
        if (noticeGivenOn == null) return null;
        if (endType == null) return "Say how employment ended before giving the date notice was given.";
        if (lastDay != null && noticeGivenOn.isAfter(lastDay)) return "Notice cannot be given after the last day worked.";
        if (start != null && noticeGivenOn.isBefore(start)) return "Notice cannot be given before the start date.";
        return null;
    }

    /** Days of notice the BCEA requires, by length of service on the day notice was given (or on the last day if that is not known). */
    public static int requiredDays(LocalDate start, LocalDate noticeGivenOn, LocalDate lastDay) {
        LocalDate on = noticeGivenOn != null ? noticeGivenOn : lastDay;
        if (start == null || on == null) return 0;
        if (!on.isAfter(start.plusMonths(6))) return 7;
        if (on.isBefore(start.plusYears(1))) return 14;
        return 28;
    }

    /** Whole days between the day notice was given and the last day worked. No date means none was given. */
    public static int givenDays(LocalDate noticeGivenOn, LocalDate lastDay) {
        if (noticeGivenOn == null || lastDay == null) return 0;
        return (int) Math.max(0, ChronoUnit.DAYS.between(noticeGivenOn, lastDay));
    }

    /** Days of notice not given. Only an employer ending employment owes pay in place of it. */
    public static int shortfallDays(String endType, LocalDate start, LocalDate noticeGivenOn, LocalDate lastDay) {
        if (!EMPLOYER_NOTICE.equals(endType)) return 0;
        return Math.max(0, requiredDays(start, noticeGivenOn, lastDay) - givenDays(noticeGivenOn, lastDay));
    }

    /** A week's pay is a year's pay over 52; the shortfall in days is paid at that rate. */
    public static BigDecimal pay(BigDecimal monthlySalary, int shortfallDays) {
        if (monthlySalary == null || shortfallDays <= 0) return BigDecimal.ZERO;
        return monthlySalary.multiply(BigDecimal.valueOf(12L * shortfallDays)).divide(BigDecimal.valueOf(364), 2, RoundingMode.HALF_UP);
    }

    public static String typeLabel(String endType) {
        if (endType == null) return "not recorded";
        return switch (endType) {
            case RESIGNATION -> "resignation";
            case EMPLOYER_NOTICE -> "ended by the employer, with notice owed";
            case SUMMARY_DISMISSAL -> "summary dismissal";
            case CONTRACT_END -> "end of a fixed-term contract";
            case MUTUAL -> "mutual agreement";
            default -> "other";
        };
    }

    /** One plain paragraph for the person ending employment and for the final-pay review. Null when there is nothing to say. */
    public static String summary(String endType, BigDecimal monthlySalary, LocalDate start, LocalDate noticeGivenOn, LocalDate lastDay) {
        if (!EMPLOYER_NOTICE.equals(endType)) return null;
        int required = requiredDays(start, noticeGivenOn, lastDay);
        int given = givenDays(noticeGivenOn, lastDay);
        int short_ = shortfallDays(endType, start, noticeGivenOn, lastDay);
        String base = "Notice required by the BCEA: " + required + " days. Notice given: " + given + " days.";
        if (short_ == 0) return base + " No pay in place of notice is owed.";
        return base + " Pay in place of the " + short_ + " days not given is about " + pay(monthlySalary, short_)
                + " before tax. Enter it as a payment if it is owed. Farm and domestic workers have longer notice after six months.";
    }
}
