package za.co.handyflow.platform.hr.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What every employee relations case shares: the three kinds, the stages and who may move a case between them, the categories, who may
 * see it, and working-day arithmetic. Discipline, grievances and incapacity have their own rules in {@link DisciplineRules},
 * {@link GrievanceRules} and {@link IncapacityRules}. No database or framework in here.
 */
public final class ErRules {

    private ErRules() {}

    public static final String DISCIPLINARY = "DISCIPLINARY";
    public static final String GRIEVANCE    = "GRIEVANCE";
    public static final String INCAPACITY   = "INCAPACITY";
    public static final List<String> TYPES = List.of(DISCIPLINARY, GRIEVANCE, INCAPACITY);

    public static final String REPORTED = "REPORTED", INVESTIGATING = "INVESTIGATING", NOTICE_ISSUED = "NOTICE_ISSUED",
            HEARING_HELD = "HEARING_HELD", DECIDED = "DECIDED", APPEALED = "APPEALED", CLOSED = "CLOSED", WITHDRAWN = "WITHDRAWN";

    public static final List<String> DISCIPLINE_CATEGORIES = List.of("ABSENTEEISM", "LATENESS", "INSUBORDINATION", "DISHONESTY", "THEFT",
            "ASSAULT", "HARASSMENT", "SUBSTANCE", "NEGLIGENCE", "SAFETY", "POLICY_BREACH", "MISUSE_OF_PROPERTY", "OTHER");
    public static final List<String> GRIEVANCE_CATEGORIES = List.of("PAY_AND_BENEFITS", "WORKING_CONDITIONS", "TREATMENT_BY_MANAGER",
            "DISCRIMINATION", "HARASSMENT", "VICTIMISATION", "HEALTH_AND_SAFETY", "WORKLOAD", "OTHER");
    public static final List<String> INCAPACITY_CATEGORIES = List.of("POOR_PERFORMANCE", "ILL_HEALTH");

    public static List<String> categories(String type) {
        if (DISCIPLINARY.equals(type)) return DISCIPLINE_CATEGORIES;
        if (GRIEVANCE.equals(type)) return GRIEVANCE_CATEGORIES;
        if (INCAPACITY.equals(type)) return INCAPACITY_CATEGORIES;
        return List.of();
    }

    /** Null when the type and category belong together. */
    public static String categoryProblem(String type, String category) {
        if (type == null || !TYPES.contains(type)) return "Choose a type of case: discipline, grievance or incapacity.";
        if (category == null || !categories(type).contains(category)) return "That category does not belong to this type of case.";
        return null;
    }

    /** Medical information and complaints of harassment or discrimination are limited to people trusted with sensitive records. */
    public static boolean confidentialByDefault(String type, String category) {
        if (INCAPACITY.equals(type)) return "ILL_HEALTH".equals(category);
        if (GRIEVANCE.equals(type)) return Set.of("DISCRIMINATION", "HARASSMENT", "VICTIMISATION").contains(category);
        return DISCIPLINARY.equals(type) && "HARASSMENT".equals(category);
    }

    public static boolean isOpen(String status) { return !CLOSED.equals(status) && !WITHDRAWN.equals(status); }

    /** Whether a case of this type may go from one stage to another. Withdrawing is allowed from any open stage before a decision. */
    public static boolean canMove(String type, String from, String to) {
        if (from == null || to == null || !isOpen(from)) return false;
        if (WITHDRAWN.equals(to)) return Set.of(REPORTED, INVESTIGATING, NOTICE_ISSUED, HEARING_HELD).contains(from);
        return switch (from) {
            case REPORTED -> switch (type) {
                case DISCIPLINARY -> Set.of(INVESTIGATING, DECIDED, CLOSED).contains(to);
                default -> INVESTIGATING.equals(to);
            };
            case INVESTIGATING -> switch (type) {
                case DISCIPLINARY -> Set.of(NOTICE_ISSUED, DECIDED, CLOSED).contains(to);
                case INCAPACITY -> NOTICE_ISSUED.equals(to);
                default -> DECIDED.equals(to);
            };
            case NOTICE_ISSUED -> Set.of(NOTICE_ISSUED, HEARING_HELD).contains(to) && !GRIEVANCE.equals(type);
            case HEARING_HELD -> DECIDED.equals(to) || (INCAPACITY.equals(type) && INVESTIGATING.equals(to));
            case DECIDED -> Set.of(APPEALED, CLOSED).contains(to);
            case APPEALED -> CLOSED.equals(to);
            default -> false;
        };
    }

    public static boolean canRead(Set<String> held) { return any(held, "HR_ER_READ", "HR_ER_MANAGE", "HR_ER_DECIDE"); }
    public static boolean canManage(Set<String> held) { return any(held, "HR_ER_MANAGE"); }
    public static boolean canDecide(Set<String> held) { return any(held, "HR_ER_DECIDE"); }

    /** Confidential cases also need HR_SENSITIVE_READ, unless the person is the one who owns the case. */
    public static boolean canSee(Set<String> held, UUID userId, boolean confidential, UUID ownerUserId) {
        if (!canRead(held)) return false;
        if (!confidential) return true;
        return held.contains("HR_SENSITIVE_READ") || (userId != null && userId.equals(ownerUserId));
    }

    /** The date a number of working days (Monday to Friday) after a date. Public holidays are not skipped; see the overload that takes them. */
    public static LocalDate addWorkingDays(LocalDate from, int days) { return addWorkingDays(from, days, Set.of()); }

    /** The date a number of working days after a date, skipping weekends and the public holidays given. */
    public static LocalDate addWorkingDays(LocalDate from, int days, Set<LocalDate> holidays) {
        LocalDate d = from;
        int left = days;
        while (left > 0) {
            d = d.plusDays(1);
            DayOfWeek w = d.getDayOfWeek();
            if (w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !holidays.contains(d)) left--;
        }
        return d;
    }

    /** Null when the text is present and not too long. */
    public static String textProblem(String label, String text, int max) {
        if (text == null || text.isBlank()) return label + " is required.";
        if (text.length() > max) return label + " is too long (" + max + " characters at most).";
        return null;
    }

    private static boolean any(Set<String> held, String... wanted) {
        for (String w : wanted) if (held.contains(w)) return true;
        return false;
    }
}
