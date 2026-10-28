package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Next of kin and dependants: what must be given, and that beneficiary shares never add up to more than the whole. Pure. */
public final class RelativeRules {

    public static final String NEXT_OF_KIN = "NEXT_OF_KIN", DEPENDANT = "DEPENDANT";
    public static final int MAX_PER_EMPLOYEE = 20;

    private static final Map<String, String> RELATIONSHIPS = new LinkedHashMap<>();
    static {
        RELATIONSHIPS.put("SPOUSE", "Spouse"); RELATIONSHIPS.put("PARTNER", "Life partner"); RELATIONSHIPS.put("CHILD", "Child");
        RELATIONSHIPS.put("PARENT", "Parent"); RELATIONSHIPS.put("SIBLING", "Brother or sister"); RELATIONSHIPS.put("GUARDIAN", "Guardian");
        RELATIONSHIPS.put("FRIEND", "Friend"); RELATIONSHIPS.put("OTHER", "Other");
    }

    private RelativeRules() {}

    public static Map<String, String> relationships() { return RELATIONSHIPS; }
    public static String relationshipLabel(String key) { return RELATIONSHIPS.getOrDefault(key, key); }

    /** What is stored: text trimmed, blanks as null, and the percentage only when the person is a beneficiary. */
    public record Details(String kind, String fullName, String relationship, String phone, String email, LocalDate dateOfBirth, String address,
                          boolean beneficiary, BigDecimal beneficiaryPercent, String notes) {}

    public record Share(java.util.UUID id, boolean beneficiary, BigDecimal percent) {}

    public static Details clean(String kind, String fullName, String relationship, String phone, String email, LocalDate dob, String address,
                                Boolean beneficiary, BigDecimal percent, String notes) {
        boolean b = Boolean.TRUE.equals(beneficiary);
        return new Details(blank(kind), blank(fullName), blank(relationship) == null ? null : relationship.trim().toUpperCase(), blank(phone),
                blank(email), dob, blank(address), b, b ? percent : null, blank(notes));
    }

    /** Null when fine. */
    public static String problem(Details d, LocalDate today, int alreadyOnFile) {
        if (alreadyOnFile >= MAX_PER_EMPLOYEE) return "There are already " + MAX_PER_EMPLOYEE + " people on this record.";
        if (!NEXT_OF_KIN.equals(d.kind()) && !DEPENDANT.equals(d.kind())) return "Choose next of kin or dependant.";
        if (d.fullName() == null) return "Give the person's full name.";
        if (d.fullName().length() > 120) return "The name is too long (120 characters at most).";
        if (!RELATIONSHIPS.containsKey(d.relationship())) return "Choose how this person is related to the employee.";
        if (NEXT_OF_KIN.equals(d.kind()) && d.phone() == null) return "Give a phone number, so the person can be reached.";
        String p = EmployeeRules.phoneProblem(d.phone());
        if (p != null) return p;
        String m = EmployeeRules.emailProblem(d.email());
        if (m != null) return m;
        if (d.dateOfBirth() != null && d.dateOfBirth().isAfter(today)) return "The date of birth is in the future.";
        if (d.dateOfBirth() != null && d.dateOfBirth().isBefore(today.minusYears(120))) return "The date of birth is too long ago.";
        if (DEPENDANT.equals(d.kind()) && d.dateOfBirth() == null) return "Give the dependant's date of birth.";
        if (d.address() != null && d.address().length() > 300) return "The address is too long (300 characters at most).";
        if (d.notes() != null && d.notes().length() > 300) return "The note is too long (300 characters at most).";
        if (d.beneficiary() && d.beneficiaryPercent() != null
                && (d.beneficiaryPercent().signum() < 0 || d.beneficiaryPercent().compareTo(BigDecimal.valueOf(100)) > 0))
            return "A beneficiary's share must be between 0 and 100 percent.";
        return null;
    }

    /** Null when the shares of everyone marked as a beneficiary, with {@code d} as {@code editing} (or new when null), stay within 100%. */
    public static String sharesProblem(List<Share> others, java.util.UUID editing, Details d) {
        BigDecimal total = BigDecimal.ZERO;
        for (Share s : others) if (s.beneficiary() && s.percent() != null && !s.id().equals(editing)) total = total.add(s.percent());
        if (d.beneficiary() && d.beneficiaryPercent() != null) total = total.add(d.beneficiaryPercent());
        return total.compareTo(BigDecimal.valueOf(100)) > 0
                ? "Beneficiary shares add up to " + total.stripTrailingZeros().toPlainString() + "%, which is more than 100%." : null;
    }

    public static Integer age(LocalDate dob, LocalDate today) {
        return dob == null || dob.isAfter(today) ? null : Period.between(dob, today).getYears();
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
