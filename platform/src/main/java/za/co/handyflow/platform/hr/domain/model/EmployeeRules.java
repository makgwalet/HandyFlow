package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

/**
 * What a valid employee record looks like. Pure, so it runs without a database. Every method returns a sentence for the
 * person fixing the form, or null when the value is fine. Blank optional values are fine.
 */
public final class EmployeeRules {

    /** The Basic Conditions of Employment Act sets 15 as the youngest age at which a person may be employed. */
    public static final int MINIMUM_AGE = 15;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9][0-9 ()-]{6,24}$");

    private EmployeeRules() {}

    /** What a South African ID number says about its holder. */
    public record IdInfo(LocalDate dateOfBirth, String gender, boolean citizen) {}

    public static String normaliseId(String id) {
        return id == null ? null : id.replaceAll("\\s", "");
    }

    /** @return the facts in the number, or null when it is not a valid 13-digit South African ID */
    public static IdInfo parseId(String raw, LocalDate today) {
        String id = normaliseId(raw);
        if (id == null || !id.matches("\\d{13}")) return null;
        int yy = Integer.parseInt(id.substring(0, 2));
        int mm = Integer.parseInt(id.substring(2, 4));
        int dd = Integer.parseInt(id.substring(4, 6));
        LocalDate dob;
        try {
            dob = LocalDate.of(2000 + yy, mm, dd);
            if (dob.isAfter(today)) dob = LocalDate.of(1900 + yy, mm, dd);
        } catch (java.time.DateTimeException e) {
            return null;
        }
        if (!luhn(id)) return null;
        int sequence = Integer.parseInt(id.substring(6, 10));
        return new IdInfo(dob, sequence < 5000 ? "FEMALE" : "MALE", id.charAt(10) == '0');
    }

    public static String idProblem(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) return null;
        if (!normaliseId(raw).matches("\\d{13}")) return "The ID number must be 13 digits.";
        return parseId(raw, today) == null ? "This is not a valid South African ID number. Check it for a typing mistake." : null;
    }

    /** The date of birth must agree with the one inside a valid ID number, and the person must be old enough to work. */
    public static String dobProblem(String idNumber, LocalDate dob, LocalDate startDate, LocalDate today) {
        if (dob != null) {
            if (dob.isAfter(today)) return "The date of birth is in the future.";
            if (startDate != null && Period.between(dob, startDate).getYears() < MINIMUM_AGE)
                return "The employee would be younger than " + MINIMUM_AGE + " on the start date.";
        }
        IdInfo info = parseId(idNumber, today);
        if (info != null && dob != null && !info.dateOfBirth().equals(dob))
            return "The date of birth does not match the ID number (" + info.dateOfBirth() + ").";
        return null;
    }

    public static String taxNumberProblem(String tax) {
        if (tax == null || tax.isBlank()) return null;
        return tax.replaceAll("\\s", "").matches("\\d{10}") ? null : "A SARS tax number is 10 digits.";
    }

    public static String emailProblem(String email) {
        if (email == null || email.isBlank()) return null;
        return EMAIL.matcher(email.trim()).matches() ? null : "This email address does not look right.";
    }

    public static String phoneProblem(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return PHONE.matcher(phone.trim()).matches() ? null : "This phone number does not look right.";
    }

    /** Bank name, account and branch go together: a number without a bank, or digits that cannot be an account, are refused. */
    public static String bankProblem(String bankName, String account, String branch) {
        boolean hasAccount = account != null && !account.isBlank();
        if (!hasAccount && (branch == null || branch.isBlank()) && (bankName == null || bankName.isBlank())) return null;
        if (bankName == null || bankName.isBlank()) return "Enter the bank name.";
        if (!hasAccount) return "Enter the account number.";
        if (!account.replaceAll("\\s", "").matches("\\d{4,17}")) return "The account number must be 4 to 17 digits.";
        if (branch != null && !branch.isBlank() && !branch.replaceAll("\\s", "").matches("\\d{6}")) return "The branch code must be 6 digits.";
        return null;
    }

    /**
     * A termination is recorded for a last day worked that has come, with a reason. Recording a future last day would take the
     * person off payroll before their final pay is due.
     */
    public static String terminationProblem(String status, LocalDate start, LocalDate end, String reason, LocalDate today) {
        if ("TERMINATED".equals(status)) return "This employee has already been terminated.";
        if (end == null) return "Enter the last day worked.";
        if (start != null && end.isBefore(start)) return "The last day worked is before the start date.";
        if (end.isAfter(today)) return "The last day worked is in the future. Record the termination on or after that day, so final pay is not missed.";
        if (reason == null || reason.isBlank()) return "Give a reason for the termination.";
        if (reason.trim().length() > 200) return "The reason can be at most 200 characters.";
        return null;
    }

    /** •••• 1234: enough to recognise an account, not enough to use it. */
    public static String maskAccount(String account) {
        if (account == null || account.isBlank()) return null;
        String digits = account.replaceAll("\\s", "");
        return digits.length() <= 4 ? "••••" : "••••" + digits.substring(digits.length() - 4);
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
