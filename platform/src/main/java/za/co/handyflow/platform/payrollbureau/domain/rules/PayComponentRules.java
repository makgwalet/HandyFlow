package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** What a pay component does to a payslip (phase 1, slice 1e). The role fixes the tax, UIF and SDL treatment. */
public final class PayComponentRules {

    public static final String TAXABLE_EARNING = "TAXABLE_EARNING";
    public static final String BONUS = "BONUS";
    public static final String NON_TAXABLE_EARNING = "NON_TAXABLE_EARNING";
    public static final String DEDUCTION = "DEDUCTION";
    public static final Set<String> ROLES = Set.of(TAXABLE_EARNING, BONUS, NON_TAXABLE_EARNING, DEDUCTION);

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");

    /** A component an amount is entered against: its role. */
    public record Line(String role, BigDecimal amount) {}

    /** What the lines add up to, by treatment. */
    public record Totals(BigDecimal taxable, BigDecimal bonus, BigDecimal nonTaxable, BigDecimal deductions) {
        public static final Totals NONE = new Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        /** Everything added to pay: the amount the payslip's earnings grow by. */
        public BigDecimal extraEarnings() { return taxable.add(bonus).add(nonTaxable); }
    }

    /** A standard component made for every tenant on first use. */
    public record Standard(String code, String name, String role, String sarsCode) {}

    /**
     * SARS source codes are a reference for the bureau's own filing and are to be checked against SARS's current code list
     * ("Guide for Codes Applicable to Employees Tax Certificates") before being relied on. Deductions that are not on an IRP5 carry none.
     */
    public static final List<Standard> STANDARD = List.of(
            new Standard("OVERTIME", "Overtime", TAXABLE_EARNING, "3607"),
            new Standard("COMMISSION", "Commission", TAXABLE_EARNING, "3606"),
            new Standard("OTHER_TAXABLE", "Other taxable earnings", TAXABLE_EARNING, "3601"),
            new Standard("BONUS", "Bonus", BONUS, "3605"),
            new Standard("REIMBURSEMENT", "Expense reimbursement", NON_TAXABLE_EARNING, "3602"),
            new Standard("LOAN_REPAYMENT", "Loan repayment", DEDUCTION, null),
            new Standard("GARNISHEE", "Garnishee order", DEDUCTION, null),
            new Standard("UNION_FEES", "Union fees", DEDUCTION, null),
            new Standard("OTHER_DEDUCTION", "Other deduction", DEDUCTION, null));

    private PayComponentRules() {}

    public static boolean earning(String role) {
        return TAXABLE_EARNING.equals(role) || BONUS.equals(role) || NON_TAXABLE_EARNING.equals(role);
    }

    /** Why a component cannot be made like this, or null. */
    public static String componentProblem(String code, String name, String role, String sarsCode) {
        if (code == null || code.isBlank()) return "Give the component a code.";
        if (!code.trim().matches("[A-Za-z0-9_]{1,30}")) return "A component code is letters, digits and underscores, up to 30.";
        if (name == null || name.isBlank()) return "Give the component a name.";
        if (name.trim().length() > 100) return "A component name is at most 100 characters.";
        if (role == null || !ROLES.contains(role)) return "Role must be TAXABLE_EARNING, BONUS, NON_TAXABLE_EARNING or DEDUCTION.";
        if (sarsCode != null && !sarsCode.isBlank() && !sarsCode.trim().matches("[0-9]{4}")) return "A SARS source code is four digits.";
        return null;
    }

    /** Why an amount cannot be used, or null. */
    public static String amountProblem(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return "The amount must be more than zero. To stop paying a component, remove it.";
        if (amount.compareTo(MAX_AMOUNT) > 0) return "That amount is too large.";
        if (amount.scale() > 2) return "Amounts have at most two decimal places.";
        return null;
    }

    public static Totals summarise(List<Line> lines) {
        BigDecimal taxable = BigDecimal.ZERO, bonus = BigDecimal.ZERO, nonTaxable = BigDecimal.ZERO, deductions = BigDecimal.ZERO;
        for (Line l : lines) {
            switch (l.role()) {
                case TAXABLE_EARNING -> taxable = taxable.add(l.amount());
                case BONUS -> bonus = bonus.add(l.amount());
                case NON_TAXABLE_EARNING -> nonTaxable = nonTaxable.add(l.amount());
                case DEDUCTION -> deductions = deductions.add(l.amount());
                default -> throw new IllegalArgumentException("Unknown component role: " + l.role());
            }
        }
        return new Totals(taxable, bonus, nonTaxable, deductions);
    }
}
