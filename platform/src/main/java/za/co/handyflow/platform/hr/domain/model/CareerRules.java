package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** What goes on an employee's career timeline, and what may be added by hand. Pure. */
public final class CareerRules {

    public static final String HIRED = "HIRED", TITLE_CHANGED = "TITLE_CHANGED", DEPARTMENT_CHANGED = "DEPARTMENT_CHANGED", MANAGER_CHANGED = "MANAGER_CHANGED",
            TYPE_CHANGED = "EMPLOYMENT_TYPE_CHANGED", SALARY_CHANGED = "SALARY_CHANGED", TERMINATED = "TERMINATED";
    public static final String PRIOR_EMPLOYMENT = "PRIOR_EMPLOYMENT", PROMOTION = "PROMOTION", TRANSFER = "TRANSFER", OTHER = "OTHER";
    public static final List<String> MANUAL = List.of(PRIOR_EMPLOYMENT, PROMOTION, TRANSFER, OTHER);

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put(HIRED, "Joined"); LABELS.put(TITLE_CHANGED, "Job title changed"); LABELS.put(DEPARTMENT_CHANGED, "Department changed");
        LABELS.put(MANAGER_CHANGED, "Manager changed"); LABELS.put(TYPE_CHANGED, "Employment type changed"); LABELS.put(SALARY_CHANGED, "Salary changed");
        LABELS.put(TERMINATED, "Left"); LABELS.put(PRIOR_EMPLOYMENT, "Earlier employer"); LABELS.put(PROMOTION, "Promotion");
        LABELS.put(TRANSFER, "Transfer"); LABELS.put(OTHER, "Other");
    }

    private CareerRules() {}

    public static String label(String kind) { return LABELS.getOrDefault(kind, kind); }

    public record Change(String kind, String from, String to) {}

    /** The title, department and employment type changes between two states. Blank and null count as the same. */
    public static List<Change> changes(String oldTitle, String newTitle, String oldDept, String newDept, String oldType, String newType) {
        List<Change> out = new ArrayList<>();
        if (differs(oldTitle, newTitle)) out.add(new Change(TITLE_CHANGED, norm(oldTitle), norm(newTitle)));
        if (differs(oldDept, newDept)) out.add(new Change(DEPARTMENT_CHANGED, norm(oldDept), norm(newDept)));
        if (differs(oldType, newType)) out.add(new Change(TYPE_CHANGED, norm(oldType), norm(newType)));
        return out;
    }

    /** Null when an entry by hand may be added. {@code startDate} is the day the person joined this company. */
    public static String manualProblem(String kind, LocalDate date, LocalDate endDate, String employer, String jobTitle, String department, String note,
                                       LocalDate startDate, LocalDate today) {
        if (kind == null || !MANUAL.contains(kind)) return "Choose earlier employer, promotion, transfer or other.";
        if (date == null) return "Give the date.";
        if (date.isAfter(today.plusYears(1))) return "The date is more than a year ahead.";
        if (note != null && note.trim().length() > 500) return "The note is too long (500 characters at most).";
        if (len(employer) > 150 || len(jobTitle) > 120 || len(department) > 100) return "One of the names is too long.";
        if (PRIOR_EMPLOYMENT.equals(kind)) {
            if (blank(employer)) return "Give the earlier employer.";
            if (blank(jobTitle)) return "Give the job title held there.";
            if (endDate == null) return "Give the date the person left that job.";
            if (endDate.isBefore(date)) return "The person cannot leave a job before starting it.";
            if (startDate != null && endDate.isAfter(startDate)) return "An earlier job must end on or before the day the person joined this company.";
            return null;
        }
        if (endDate != null) return "Only an earlier employer has an end date.";
        if (startDate != null && date.isBefore(startDate)) return "This is dated before the person joined the company.";
        if (PROMOTION.equals(kind) && blank(jobTitle)) return "Give the job title the person was promoted to.";
        if (TRANSFER.equals(kind) && blank(department)) return "Give the department the person moved to.";
        if (OTHER.equals(kind) && blank(note)) return "Say what happened.";
        return null;
    }

    /** Percentage change to one decimal place, or null when it cannot be worked out. */
    public static BigDecimal percent(String from, String to) {
        try {
            BigDecimal f = new BigDecimal(from), t = new BigDecimal(to);
            if (f.signum() <= 0) return null;
            return t.subtract(f).multiply(BigDecimal.valueOf(100)).divide(f, 1, RoundingMode.HALF_UP);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean differs(String a, String b) { return !Objects.equals(norm(a), norm(b)); }
    private static String norm(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static int len(String s) { return s == null ? 0 : s.trim().length(); }
}
