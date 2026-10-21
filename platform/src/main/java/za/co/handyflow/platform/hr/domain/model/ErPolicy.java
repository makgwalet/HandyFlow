package za.co.handyflow.platform.hr.domain.model;

/**
 * The company's own employee relations settings: the values the law leaves to the employer. The Code of Good Practice: Dismissal asks for
 * reasonable time to prepare and a fair process, but gives no fixed hours of notice, no warning lifespan, no appeal period, and there is
 * no private-sector grievance deadline. The defaults are common practice, not law, and each company can change them.
 */
public record ErPolicy(int noticeHours, int verbalMonths, int writtenMonths, int finalMonths,
                       int appealDays, int grievanceResponseDays, int minReviewDays) {

    public static ErPolicy defaults() { return new ErPolicy(48, 3, 6, 12, 7, 5, 28); }

    /** Months a warning of this level stays valid, or 0 when the sanction is not a warning. */
    public int monthsFor(String sanction) {
        if (sanction == null) return 0;
        return switch (sanction) {
            case "VERBAL_WARNING" -> verbalMonths;
            case "WRITTEN_WARNING" -> writtenMonths;
            case "FINAL_WRITTEN_WARNING" -> finalMonths;
            default -> 0;
        };
    }

    /** Null when the settings are usable, otherwise what is wrong. */
    public String problem() {
        if (noticeHours < 1 || noticeHours > 720) return "Notice of a hearing must be between 1 and 720 hours.";
        if (!months(verbalMonths) || !months(writtenMonths) || !months(finalMonths)) return "A warning must stay valid for between 1 and 60 months.";
        if (verbalMonths > writtenMonths || writtenMonths > finalMonths)
            return "A lower warning cannot stay valid longer than a higher one (verbal, then written, then final).";
        if (appealDays < 1 || appealDays > 90) return "The appeal period must be between 1 and 90 days.";
        if (grievanceResponseDays < 1 || grievanceResponseDays > 60) return "The grievance response time must be between 1 and 60 working days.";
        if (minReviewDays < 1 || minReviewDays > 365) return "The minimum review period must be between 1 and 365 days.";
        return null;
    }

    private static boolean months(int m) { return m >= 1 && m <= 60; }
}
