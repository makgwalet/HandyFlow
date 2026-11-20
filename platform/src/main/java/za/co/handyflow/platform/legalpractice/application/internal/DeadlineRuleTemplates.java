package za.co.handyflow.platform.legalpractice.application.internal;

import java.util.List;

/**
 * Starting rules a firm can install in one click. They are NOT legal advice: each is a plain reading of a well-known
 * period, and a lawyer at the firm must confirm it against the current rules before relying on it. Installing adds only
 * the codes the firm does not already have, so edited rules are never overwritten.
 */
public final class DeadlineRuleTemplates {

    public record Template(String code, String name, String description, String court, String triggerLabel, String keyDateType,
                           int amount, String unit, String direction, String rollover, String recessStart, String recessEnd,
                           String sourceReference) {}

    private static final String CHECK = " Starting point from the template pack: confirm it against the current rules before relying on it.";

    public static final List<Template> ALL = List.of(
            new Template("HC_NOTICE_TO_DEFEND", "Notice of intention to defend (summons)",
                    "Ten court days to enter appearance to defend after the summons is served; the December recess is not counted." + CHECK,
                    "HIGH_COURT", "Date the summons was served", "FILING_DEADLINE", 10, "COURT_DAYS", "AFTER", "NONE", "12-16", "01-15",
                    "Uniform Rule 19(1), Rule 26"),
            new Template("HC_PLEA", "Plea",
                    "Twenty court days after the notice of intention to defend is delivered; the December recess is not counted." + CHECK,
                    "HIGH_COURT", "Date the notice of intention to defend was delivered", "FILING_DEADLINE", 20, "COURT_DAYS", "AFTER", "NONE", "12-16", "01-15",
                    "Uniform Rule 22(1), Rule 26"),
            new Template("HC_NOTICE_TO_OPPOSE", "Notice of intention to oppose (application)",
                    "Five court days after an application is served." + CHECK,
                    "HIGH_COURT", "Date the application was served", "FILING_DEADLINE", 5, "COURT_DAYS", "AFTER", "NONE", null, null,
                    "Uniform Rule 6(5)(d)(i)"),
            new Template("HC_ANSWERING_AFFIDAVIT", "Answering affidavit (application)",
                    "Fifteen court days after the notice of intention to oppose is delivered." + CHECK,
                    "HIGH_COURT", "Date the notice of intention to oppose was delivered", "FILING_DEADLINE", 15, "COURT_DAYS", "AFTER", "NONE", null, null,
                    "Uniform Rule 6(5)(d)(ii)"),
            new Template("HC_LEAVE_TO_APPEAL", "Application for leave to appeal",
                    "Fifteen court days after the judgment or order." + CHECK,
                    "HIGH_COURT", "Date of the judgment or order", "FILING_DEADLINE", 15, "COURT_DAYS", "AFTER", "NONE", null, null,
                    "Uniform Rule 49(1)(b)"),
            new Template("PRESCRIPTION_3Y", "Prescription of a debt (three years)",
                    "Three years from the day the debt became due. Working out WHEN the debt became due, and whether prescription was interrupted or delayed, is for the attorney: this rule only does the arithmetic." + CHECK,
                    "OTHER", "Date the debt became due", "PRESCRIPTION_DEADLINE", 3, "YEARS", "AFTER", "NONE", null, null,
                    "Prescription Act 68 of 1969, ss 11(d), 12"),
            new Template("LRA_UNFAIR_DISMISSAL", "Refer an unfair dismissal dispute (CCMA / bargaining council)",
                    "Thirty days from the date of dismissal; a last day that is a Sunday or public holiday moves on. Late referrals need condonation." + CHECK,
                    "LABOUR", "Date of dismissal", "FILING_DEADLINE", 30, "CALENDAR_DAYS", "AFTER", "SKIP_SUNDAY_AND_HOLIDAYS", null, null,
                    "Labour Relations Act 66 of 1995, s 191(1)(b)(i)"),
            new Template("LRA_UNFAIR_LABOUR_PRACTICE", "Refer an unfair labour practice dispute",
                    "Ninety days from the date of the act or omission; a last day that is a Sunday or public holiday moves on." + CHECK,
                    "LABOUR", "Date of the act or omission", "FILING_DEADLINE", 90, "CALENDAR_DAYS", "AFTER", "SKIP_SUNDAY_AND_HOLIDAYS", null, null,
                    "Labour Relations Act 66 of 1995, s 191(1)(b)(ii)")
    );

    private DeadlineRuleTemplates() {}
}
