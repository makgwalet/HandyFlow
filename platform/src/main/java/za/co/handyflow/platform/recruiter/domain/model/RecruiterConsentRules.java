package za.co.handyflow.platform.recruiter.domain.model;

/**
 * What an applicant agrees to when they apply through the careers page. The wording is versioned so a record of consent says which
 * wording was shown. Change {@link #VERSION} whenever {@link #STATEMENT} changes.
 */
public final class RecruiterConsentRules {

    private RecruiterConsentRules() {}

    public static final String VERSION = "2026-10b";

    public static final String STATEMENT =
            "I agree that this company may keep my application, CV and contact details to consider me for this and similar roles, "
            + "and that they will be removed after the company's retention period. I can ask to see, correct or delete them at any time.";

    /**
     * The statement as shown to the applicant: the same words with the company's retention period in months. Without a usable number it is
     * the general statement. Mirrored by the careers page.
     */
    public static String statementFor(Integer retentionMonths) {
        if (retentionMonths == null || retentionMonths < 1) return STATEMENT;
        return "I agree that this company may keep my application, CV and contact details to consider me for this and similar roles, "
                + "and that they will be removed " + retentionMonths + (retentionMonths == 1 ? " month" : " months")
                + " after my last activity unless I am hired. I can ask to see, correct or delete them at any time.";
    }

    /** Null when the application may go ahead. */
    public static String problem(Boolean consent) {
        return Boolean.TRUE.equals(consent) ? null : "Please agree to the use of your details before you apply.";
    }
}
