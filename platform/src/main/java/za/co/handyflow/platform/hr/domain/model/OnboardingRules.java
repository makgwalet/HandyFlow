package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Onboarding of a new employee: the standard list of things a South African employer has to get done around the start date,
 * and the rules for ticking them off.
 * <p>
 * Pure: no database and no clock. Some tasks are <em>checked</em>, not ticked: whether the ID number, bank details or manager
 * are on the employee record is read from the record, so a task cannot say "done" while the data is missing, and cannot say
 * "open" once it has been fixed. The rest are ticked by a person, who may instead waive a task with a reason.
 */
public final class OnboardingRules {

    public static final String OPEN = "OPEN", DONE = "DONE", WAIVED = "WAIVED";
    public static final String IN_PROGRESS = "IN_PROGRESS", COMPLETE = "COMPLETE", CANCELLED = "CANCELLED";
    public static final String START = "START", PROBATION_END = "PROBATION_END";

    public static final List<String> CATEGORIES = List.of("CONTRACT", "DOCUMENTS", "PAYROLL", "COMPLIANCE", "ACCESS", "INDUCTION");
    public static final int DEFAULT_PROBATION_MONTHS = 3;
    public static final int MAX_PROBATION_MONTHS = 12;

    private OnboardingRules() {}

    /** A task as the template describes it. {@code check} is null for tasks a person ticks. */
    public record Spec(String title, String description, String category, boolean required, int dueOffsetDays, String dueBasis, String check) {}

    public static final List<String> CHECKS = List.of("ID_NUMBER", "TAX_NUMBER", "BANK_DETAILS", "SALARY", "MANAGER", "LOGIN", "EMERGENCY_CONTACT");

    /** The tasks every new tenant starts with. Each tenant can change its own list afterwards. */
    public static List<Spec> defaults() {
        List<Spec> l = new ArrayList<>();
        l.add(new Spec("Written particulars of employment signed",
                "Give the employee their written particulars (contract or letter) on the day they start. BCEA section 29 sets what it must contain.",
                "CONTRACT", true, 0, START, null));
        l.add(new Spec("ID number recorded", "The employee's South African ID number, or their passport and the country that issued it, is on their record and is valid.", "DOCUMENTS", true, 0, START, "ID_NUMBER"));
        l.add(new Spec("Certified copy of ID or passport kept", "Keep a copy of the ID, or the passport and work permit for a foreign national.", "DOCUMENTS", true, 7, START, null));
        l.add(new Spec("Qualifications and references verified", "Check the qualifications and speak to the references the employee gave.", "DOCUMENTS", false, 14, START, null));
        l.add(new Spec("Emergency contact recorded", "Name and phone number of someone to call in an emergency.", "DOCUMENTS", true, 7, START, "EMERGENCY_CONTACT"));
        l.add(new Spec("Salary and pay frequency set", "The pay is on the employee's record, so the first pay run includes them correctly.", "PAYROLL", true, 0, START, "SALARY"));
        l.add(new Spec("Bank details recorded", "Bank, account number and branch code for the salary payment.", "PAYROLL", true, 3, START, "BANK_DETAILS"));
        l.add(new Spec("Income tax number recorded", "The SARS income tax reference number, for the tax certificate.", "PAYROLL", false, 7, START, "TAX_NUMBER"));
        l.add(new Spec("Medical scheme membership recorded or declined", "Record the scheme and dependants, or that the employee declined.", "PAYROLL", false, 14, START, null));
        l.add(new Spec("Retirement fund enrolment decided", "Enrol the employee if the company has a fund, or record that none applies.", "PAYROLL", false, 30, START, null));
        l.add(new Spec("Employee declared to the UIF", "Declare the employee to the Unemployment Insurance Fund.", "COMPLIANCE", true, 7, START, null));
        l.add(new Spec("Privacy notice given (POPIA)", "Tell the employee what personal information is kept and why, and record that they were told.", "COMPLIANCE", true, 7, START, null));
        l.add(new Spec("Health and safety induction", "Tell the employee about the hazards of their work and how to work safely (OHSA).", "COMPLIANCE", true, 7, START, null));
        l.add(new Spec("Policies and code of conduct acknowledged", "The employee has read the company policies and acknowledged them.", "COMPLIANCE", true, 14, START, null));
        l.add(new Spec("Reporting manager set", "Who the employee reports to is on their record.", "ACCESS", true, 3, START, "MANAGER"));
        l.add(new Spec("Equipment, workspace and system access issued", "Laptop, phone, desk, keys, email and systems the job needs.", "ACCESS", false, 3, START, null));
        l.add(new Spec("Employee portal login linked", "The employee can sign in to see payslips and leave.", "ACCESS", false, 14, START, "LOGIN"));
        l.add(new Spec("Induction held with the manager", "Walk through the role, goals, and who to ask for what.", "INDUCTION", true, 14, START, null));
        l.add(new Spec("Probation review: confirm, extend or end", "Decide before the probation period ends.", "INDUCTION", true, -7, PROBATION_END, null));
        return List.copyOf(l);
    }

    // ── Dates ───────────────────────────────────────────────────────────────────────────────────

    public static LocalDate probationEnd(LocalDate start, int months) { return start.plusMonths(months); }

    public static LocalDate dueDate(LocalDate start, LocalDate probationEnd, int offsetDays, String basis) {
        LocalDate base = PROBATION_END.equals(basis) && probationEnd != null ? probationEnd : start;
        return base.plusDays(offsetDays);
    }

    /** An error message, or null: the probation must end after the start and within a year. */
    public static String probationProblem(LocalDate start, LocalDate end) {
        if (end == null) return "Choose the date the probation period ends.";
        if (start == null || !end.isAfter(start)) return "The probation period must end after the start date.";
        if (end.isAfter(start.plusMonths(MAX_PROBATION_MONTHS))) return "A probation period of more than " + MAX_PROBATION_MONTHS + " months is almost certainly a typing mistake.";
        return null;
    }

    // ── Checks against the employee record ──────────────────────────────────────────────────────

    /** What the employee record holds, as plain facts. */
    public record Facts(boolean idNumber, boolean taxNumber, boolean bankDetails, boolean salary, boolean manager, boolean login, boolean emergencyContact) {}

    public static Facts facts(String idNumber, String taxNumber, String bankName, String account, String branch, java.math.BigDecimal gross,
                              UUID managerId, UUID userId, String emergencyName, String emergencyPhone, LocalDate today) {
        return facts(idNumber, null, null, taxNumber, bankName, account, branch, gross, managerId, userId, emergencyName, emergencyPhone, today);
    }

    /** As above. The identity check passes with a valid South African ID, or with a passport and the country that issued it. */
    public static Facts facts(String idNumber, String passportNumber, String passportCountry, String taxNumber, String bankName, String account,
                              String branch, java.math.BigDecimal gross, UUID managerId, UUID userId, String emergencyName, String emergencyPhone,
                              LocalDate today) {
        boolean id = EmployeeRules.identified(idNumber, passportNumber, passportCountry, today);
        boolean tax = taxNumber != null && !taxNumber.isBlank() && EmployeeRules.taxNumberProblem(taxNumber) == null;
        boolean bank = account != null && !account.isBlank() && EmployeeRules.bankProblem(bankName, account, branch) == null;
        boolean salary = gross != null && gross.signum() > 0;
        boolean contact = emergencyName != null && !emergencyName.isBlank() && emergencyPhone != null && !emergencyPhone.isBlank();
        return new Facts(id, tax, bank, salary, managerId != null, userId != null, contact);
    }

    public static boolean checkPasses(String check, Facts f) {
        if (check == null) return false;
        return switch (check) {
            case "ID_NUMBER" -> f.idNumber();
            case "TAX_NUMBER" -> f.taxNumber();
            case "BANK_DETAILS" -> f.bankDetails();
            case "SALARY" -> f.salary();
            case "MANAGER" -> f.manager();
            case "LOGIN" -> f.login();
            case "EMERGENCY_CONTACT" -> f.emergencyContact();
            default -> false;
        };
    }

    public static boolean isCheck(String check) { return check != null && CHECKS.contains(check); }

    // ── Task state ──────────────────────────────────────────────────────────────────────────────

    /** The state a task shows: what a person recorded, or, for a checked task still open, what the employee record says now. */
    public static String effective(String stored, String check, Facts f) {
        if (DONE.equals(stored) || WAIVED.equals(stored)) return stored;
        return checkPasses(check, f) ? DONE : OPEN;
    }

    public static boolean overdue(String effective, LocalDate due, LocalDate today) {
        return OPEN.equals(effective) && due != null && due.isBefore(today);
    }

    /** A task a person ticks. A checked task is ticked by fixing the record, so it cannot be ticked by hand. */
    public static String completeProblem(String check, String effective, Facts f) {
        if (!OPEN.equals(effective)) return "This task is already " + effective.toLowerCase() + ".";
        if (isCheck(check)) return "This task is checked against the employee record. Fix the record and it ticks itself.";
        return null;
    }

    public static String waiveProblem(String effective, String reason) {
        if (!OPEN.equals(effective)) return "This task is already " + effective.toLowerCase() + ".";
        if (reason == null || reason.isBlank()) return "Give the reason this task does not apply.";
        if (reason.length() > 500) return "Keep the reason under 500 characters.";
        return null;
    }

    public record TaskLite(String title, boolean required, String effective) {}

    /** Why onboarding cannot be finished yet: required tasks neither done nor waived. */
    public static List<String> finishProblems(List<TaskLite> tasks) {
        return tasks.stream().filter(t -> t.required() && OPEN.equals(t.effective()))
                .map(t -> "Required task not done: " + t.title()).toList();
    }

    public record Progress(int total, int finished, int requiredOpen, int overdue) {
        public int percent() { return total == 0 ? 100 : Math.round(100f * finished / total); }
    }

    public record Item(boolean required, String effective, LocalDate due) {}

    public static Progress progress(List<Item> items, LocalDate today) {
        int finished = 0, reqOpen = 0, over = 0;
        for (Item i : items) {
            if (!OPEN.equals(i.effective())) finished++;
            else if (i.required()) reqOpen++;
            if (overdue(i.effective(), i.due(), today)) over++;
        }
        return new Progress(items.size(), finished, reqOpen, over);
    }

    public static String titleProblem(String title) {
        if (title == null || title.isBlank()) return "Give the task a title.";
        if (title.trim().length() > 200) return "Keep the title under 200 characters.";
        return null;
    }

    public static String templateProblem(String title, String category, String basis, String check, int offsetDays) {
        String t = titleProblem(title);
        if (t != null) return t;
        if (category == null || !CATEGORIES.contains(category)) return "Choose a category.";
        if (basis == null || !(START.equals(basis) || PROBATION_END.equals(basis))) return "Choose whether the due date counts from the start date or the end of probation.";
        if (check != null && !check.isBlank() && !CHECKS.contains(check)) return "That check does not exist.";
        if (Math.abs(offsetDays) > 365) return "A due date more than a year from the start is almost certainly a typing mistake.";
        return null;
    }
}
