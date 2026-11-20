package za.co.handyflow.platform.legalpractice.application.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The rules for a court case, with no database: stages, what a stage move needs, dates and orders. */
public final class LitigationRules {

    private LitigationRules() { }

    public static final int MIN_TEXT = 10;
    public static final Set<String> COURT_LEVELS = Set.of("MAGISTRATES", "HIGH_COURT", "SCA", "CONSTITUTIONAL", "LABOUR", "TAX", "EQUALITY", "OTHER");
    public static final Set<String> ROLES = Set.of("PLAINTIFF", "DEFENDANT", "APPLICANT", "RESPONDENT", "APPELLANT", "OTHER");
    public static final Set<String> OUTCOMES = Set.of("WON", "LOST", "SETTLED", "WITHDRAWN", "STRUCK_OFF", "OTHER");
    public static final Set<String> HEARING_TYPES = Set.of("TRIAL", "MOTION", "APPLICATION", "CASE_MANAGEMENT", "PRE_TRIAL", "APPEAL", "JUDGMENT", "OTHER");
    /** In the order a case usually moves. CONCLUDED is reached only by concluding the case. */
    public static final List<String> STAGES = List.of("PRE_ACTION", "PLEADINGS", "DISCOVERY", "PRE_TRIAL", "TRIAL", "JUDGMENT", "APPEAL", "EXECUTION", "CONCLUDED");

    private static final Map<String, String> HEARING_LABEL = Map.of("TRIAL", "Trial", "MOTION", "Motion", "APPLICATION", "Application", "CASE_MANAGEMENT", "Case management",
            "PRE_TRIAL", "Pre-trial", "APPEAL", "Appeal", "JUDGMENT", "Judgment", "OTHER", "Hearing");

    public static boolean open(String stage) { return !"CONCLUDED".equals(stage); }

    public static String textProblem(String text, String what) {
        return text == null || text.trim().length() < MIN_TEXT ? "Say " + what + ", in at least " + MIN_TEXT + " characters" : null;
    }

    public static String caseProblem(String courtLevel, String courtName, String role, String caseNumber, String stage, LocalDate issued, LocalDate served, LocalDate today) {
        if (courtLevel == null || !COURT_LEVELS.contains(courtLevel)) return "Choose the kind of court";
        if (courtName == null || courtName.trim().length() < 2) return "Enter the name of the court";
        if (role == null || !ROLES.contains(role)) return "Choose which side the client is on";
        if (!"PRE_ACTION".equals(stage) && (caseNumber == null || caseNumber.trim().length() < 2)) return "Enter the case number. Proceedings that have been issued have one";
        if (issued != null && issued.isAfter(today)) return "The date issued cannot be in the future";
        if (served != null && served.isAfter(today)) return "The date served cannot be in the future";
        if (issued != null && served != null && served.isBefore(issued)) return "The case cannot be served before it was issued";
        return null;
    }

    /** What stops a move from one stage to another. Concluding and reopening are separate steps; going back needs a reason. */
    public static String stageProblem(String from, String to, String note) {
        if (to == null || !STAGES.contains(to)) return "Choose a stage";
        if ("CONCLUDED".equals(from)) return "This case is concluded. Reopen it first";
        if ("CONCLUDED".equals(to)) return "Conclude the case with its outcome instead";
        if (from.equals(to)) return "The case is already at that stage";
        if (STAGES.indexOf(to) < STAGES.indexOf(from)) return textProblem(note, "why the case is moving back");
        return null;
    }

    public static String concludeProblem(String outcome, LocalDate on, String note, LocalDate issued, LocalDate today) {
        if (outcome == null || !OUTCOMES.contains(outcome)) return "Choose the outcome";
        if (on == null) return "Enter the date the case ended";
        if (on.isAfter(today)) return "The date the case ended cannot be in the future";
        if (issued != null && on.isBefore(issued)) return "The case cannot end before it was issued";
        return textProblem(note, "how it ended");
    }

    public static String hearingProblem(String type, LocalDate date) {
        if (type == null || !HEARING_TYPES.contains(type)) return "Choose the kind of hearing";
        if (date == null) return "Enter the date of the hearing";
        return null;
    }

    /** Courts do not usually sit on weekends or public holidays. A warning, not a refusal: urgent matters are sometimes heard. */
    public static List<String> dateWarnings(LocalDate d) {
        List<String> w = new ArrayList<>();
        if (d == null) return w;
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) w.add("This date is a " + (d.getDayOfWeek() == DayOfWeek.SATURDAY ? "Saturday" : "Sunday") + ". Check it with the court");
        String holiday = d.getYear() >= SaPublicHolidays.FIRST_YEAR && d.getYear() <= SaPublicHolidays.LAST_YEAR ? SaPublicHolidays.nameOf(d) : null;
        if (holiday != null) w.add("This date is a public holiday (" + holiday + "). Check it with the court");
        return w;
    }

    public static String heldProblem(LocalDate hearingDate, String outcome, LocalDate today) {
        if (hearingDate.isAfter(today)) return "A hearing that has not happened yet cannot be marked as held";
        return textProblem(outcome, "what happened at the hearing");
    }

    public static String orderProblem(LocalDate orderDate, String summary, LocalDate complianceDue, LocalDate today) {
        if (orderDate == null) return "Enter the date of the order";
        if (orderDate.isAfter(today)) return "The date of the order cannot be in the future";
        if (complianceDue != null && complianceDue.isBefore(orderDate)) return "The date to comply by cannot be before the order";
        return textProblem(summary, "what the court ordered");
    }

    public static String compliedProblem(LocalDate orderDate, LocalDate on, LocalDate today) {
        if (on == null) return "Enter the date the order was complied with";
        if (on.isAfter(today)) return "The date cannot be in the future";
        if (on.isBefore(orderDate)) return "The order cannot be complied with before it was made";
        return null;
    }

    public static String hearingLabel(String type) { return HEARING_LABEL.getOrDefault(type, "Hearing"); }

    public static String hearingKeyDescription(String type, String caseNumber, String courtName) {
        String where = caseNumber != null && !caseNumber.isBlank() ? caseNumber.trim() : courtName.trim();
        return hearingLabel(type) + ": " + where;
    }

    public static String complianceKeyDescription(String summary) {
        String s = summary.trim().replaceAll("\\s+", " ");
        return "Comply with court order: " + (s.length() > 100 ? s.substring(0, 97) + "..." : s);
    }
}
