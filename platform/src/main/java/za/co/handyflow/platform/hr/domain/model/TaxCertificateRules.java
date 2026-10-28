package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tax certificates (IRP5, or IT3(a) when no tax was deducted) and the reconciliation with the EMP201 returns.
 * <p>
 * Pure: no database and no clock. What it does and does not do:
 * <ul>
 *   <li>The reconciliation year runs from 1 March to the end of February and is named after the year it ends in (the same
 *       naming as the tax year of a pay run). The interim reconciliation covers March to August; the annual one covers
 *       March to February. SARS: interim certificates are sent to SARS only and are not given to employees.</li>
 *   <li>Only the codes that map one-to-one to what a payslip holds are produced: 3601, 3605, 3607, 3713 and 3701 for
 *       income, 4001/4003/4006 and 4005 for the employee's contributions, and 4102, 4116, 4141 and 4142 for tax. The totals
 *       SARS also requires (3695 to 3699, 4149, 4582 and others) are not produced, because their definitions could not be
 *       confirmed from SARS's own documents; see {@link #NOT_PRODUCED}. A certificate from here is therefore a workpaper to
 *       complete in e@syFile, not a submission.</li>
 *   <li>Amounts are rounded to cents; zero amounts are left out (SARS: income and deduction fields with a zero value are not
 *       reported), except the tax codes 4102, 4141 and 4142, which are reported to the cent even when zero.</li>
 * </ul>
 */
public final class TaxCertificateRules {

    public static final String INTERIM = "INTERIM", ANNUAL = "ANNUAL";
    public static final String IRP5 = "IRP5", IT3A = "IT3A";
    public static final String PENSION = "PENSION", PROVIDENT = "PROVIDENT", RETIREMENT_ANNUITY = "RETIREMENT_ANNUITY";

    /** Codes SARS requires that this class does not produce, and why. Shown to the person preparing the submission. */
    public static final List<String> NOT_PRODUCED = List.of(
            "3695, 3696 (gross non-taxable income), 3699 (gross employment income, taxable; the amounts in 3696 are not part of it) and 4149 (statutory total): SARS's 2027 code guide describes them, but the full list of codes each one adds up could not be read, so they are not shown on the certificate. The e@syFile file does carry 3699 (the sum of the income codes) and 4149 (the sum of 4102, 4141 and 4142), worked out when it is made, because SARS's file layout lists them as mandatory. 3697 and 3698 have not applied since the 2017 year of assessment and are correctly left out.",
            "4582 and the taxable portion of the travel allowance: how SARS wants it reported could not be confirmed.",
            "Employer contributions and other fringe benefits are reported under 3801, 3810, 3817, 3825, 3828 and 4472 to 4475, taken from a payroll vendor's code list rather than from SARS itself, and only what payroll recorded. Payslips made before benefits were recorded carry none, and the totals codes (3695, 3696 and 3699) are not produced, so check a certificate for an employee with benefits against SARS's own code list before submitting it.");

    private TaxCertificateRules() {}

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    // ── Periods ─────────────────────────────────────────────────────────────────────────────────

    public record Window(LocalDate start, LocalDate end) {
        public boolean contains(LocalDate d) { return d != null && !d.isBefore(start) && !d.isAfter(end); }
    }

    /** The first and last day of the reconciliation: 1 March to 31 August (interim) or to the end of February (annual). */
    public static Window window(int taxYear, String period) {
        LocalDate start = LocalDate.of(taxYear - 1, 3, 1);
        if (INTERIM.equals(period)) return new Window(start, LocalDate.of(taxYear - 1, 8, 31));
        if (ANNUAL.equals(period)) return new Window(start, LocalDate.of(taxYear, 3, 1).minusDays(1));
        throw new IllegalArgumentException("The period must be INTERIM or ANNUAL.");
    }

    /** The two digits of the reconciliation period inside a certificate number: 08 or 02. */
    public static String periodDigits(String period) { return INTERIM.equals(period) ? "08" : "02"; }

    // ── Figures ─────────────────────────────────────────────────────────────────────────────────

    /** What one employee was paid and charged over the window, summed from payslips of finalised runs. */
    public record Totals(BigDecimal basic, BigDecimal overtime, BigDecimal bonus, BigDecimal otherEarnings, BigDecimal travel,
                         BigDecimal pension, BigDecimal medicalAid, BigDecimal paye, BigDecimal uifEmployee, BigDecimal uifEmployer,
                         BigDecimal sdl, BigDecimal medicalCredit, int payslips, int payslipsWithoutCredit,
                         BigDecimal employerMedical, BigDecimal employerRetirement, BigDecimal otherFringe) {
        /** Without taxable benefits, as before payroll recorded them. */
        public Totals(BigDecimal basic, BigDecimal overtime, BigDecimal bonus, BigDecimal otherEarnings, BigDecimal travel,
                      BigDecimal pension, BigDecimal medicalAid, BigDecimal paye, BigDecimal uifEmployee, BigDecimal uifEmployer,
                      BigDecimal sdl, BigDecimal medicalCredit, int payslips, int payslipsWithoutCredit) {
            this(basic, overtime, bonus, otherEarnings, travel, pension, medicalAid, paye, uifEmployee, uifEmployer, sdl, medicalCredit,
                    payslips, payslipsWithoutCredit, ZERO, ZERO, ZERO);
        }
        public static Totals empty() {
            return new Totals(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 0, 0);
        }
        public BigDecimal uif() { return uifEmployee.add(uifEmployer); }
    }

    public record Line(String code, String description, BigDecimal amount) {}

    public static BigDecimal cents(BigDecimal v) { return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP); }

    private static void add(List<Line> out, String code, String description, BigDecimal v) {
        BigDecimal a = cents(v);
        if (a.signum() > 0) out.add(new Line(code, description, a));
    }

    /** The codes and amounts of one certificate. Order is the order of the code numbers. */
    public static List<Line> lines(Totals t, String retirementFund) {
        List<Line> out = new ArrayList<>();
        add(out, "3601", "Income: salary or wages", t.basic());
        add(out, "3605", "Annual payment: bonus and leave paid out", t.bonus());
        add(out, "3607", "Overtime", t.overtime());
        add(out, "3701", "Travel allowance", t.travel());
        add(out, "3713", "Other taxable allowances", t.otherEarnings());
        String fund = retirementFund == null ? PENSION : retirementFund;
        // Taxable benefits: the employer's contribution shows as a fringe benefit and again in the 44xx code beside it.
        add(out, "3801", "General fringe benefits", t.otherFringe());
        add(out, "3810", "Medical scheme: employer contribution (taxable benefit)", t.employerMedical());
        add(out, "4474", "Employer's medical aid contributions", t.employerMedical());
        switch (fund) {
            case PROVIDENT -> {
                add(out, "3825", "Employer provident fund contributions (taxable benefit)", t.employerRetirement());
                add(out, "4473", "Employer's provident fund contributions", t.employerRetirement());
            }
            case RETIREMENT_ANNUITY -> {
                add(out, "3828", "Employer retirement annuity contributions (taxable benefit)", t.employerRetirement());
                add(out, "4475", "Employer's retirement annuity contributions", t.employerRetirement());
            }
            default -> {
                add(out, "3817", "Employer pension fund contributions (taxable benefit)", t.employerRetirement());
                add(out, "4472", "Employer's pension fund contributions", t.employerRetirement());
            }
        }
        switch (fund) {
            case PROVIDENT -> add(out, "4003", "Provident fund contributions (employee)", t.pension());
            case RETIREMENT_ANNUITY -> add(out, "4006", "Retirement annuity contributions (employee)", t.pension());
            default -> add(out, "4001", "Pension fund contributions (employee)", t.pension());
        }
        add(out, "4005", "Medical scheme contributions (employee)", t.medicalAid());
        // Tax codes are reported to the cent even when zero.
        out.add(new Line("4102", "PAYE (employees' tax)", cents(t.paye())));
        add(out, "4116", "Medical tax credit applied against PAYE", t.medicalCredit());
        out.add(new Line("4141", "UIF contributions (employee and employer)", cents(t.uif())));
        out.add(new Line("4142", "Skills development levy", cents(t.sdl())));
        out.sort(java.util.Comparator.comparing(Line::code));
        return out;
    }

    /** True when something is reported as income. SARS: at least one income code with a value above zero. */
    public static boolean hasIncome(List<Line> lines) {
        return lines.stream().anyMatch(l -> l.code().startsWith("36") || l.code().startsWith("37"));
    }

    /** IRP5 when tax was deducted, IT3(a) when none was. */
    public static String certificateType(Totals t) { return t.paye().signum() > 0 ? IRP5 : IT3A; }

    // ── Certificate number ──────────────────────────────────────────────────────────────────────

    /**
     * Thirty digits: the employer's 10-digit PAYE reference, the 4-digit reconciliation year, the 2 digits of the period (08 or 02),
     * then 14 digits that make it unique. SARS does not allow a number to be reused, even one that was cancelled, so the tail comes from a sequence.
     */
    public static String certificateNumber(String payeReference, int taxYear, String period, long sequence) {
        String ref = payeReference == null ? "" : payeReference.replaceAll("\\s", "");
        if (!ref.matches("\\d{10}")) throw new IllegalArgumentException("The employer PAYE reference must be 10 digits.");
        if (sequence < 0 || sequence > 99_999_999_999_999L) throw new IllegalArgumentException("The certificate sequence is out of range.");
        return ref + String.format("%04d", taxYear) + periodDigits(period) + String.format("%014d", sequence);
    }

    // ── Checks ──────────────────────────────────────────────────────────────────────────────────

    public record Problem(String severity, String subject, String message) {
        public boolean isError() { return "ERROR".equals(severity); }
    }

    private static Problem error(String subject, String msg) { return new Problem("ERROR", subject, msg); }
    private static Problem warning(String subject, String msg) { return new Problem("WARNING", subject, msg); }

    /** The employer's details SARS needs on every certificate. */
    public static List<Problem> employerProblems(String payeRef, String sdlRef, String uifRef) {
        List<Problem> out = new ArrayList<>();
        if (payeRef == null || !payeRef.replaceAll("\\s", "").matches("\\d{10}"))
            out.add(error("Employer", "Enter the employer's 10-digit PAYE reference number in the tax settings."));
        if (sdlRef == null || sdlRef.isBlank()) out.add(warning("Employer", "No SDL reference number is recorded."));
        if (uifRef == null || uifRef.isBlank()) out.add(warning("Employer", "No UIF reference number is recorded."));
        return out;
    }

    /** Problems with one employee's certificate. Errors stop it from being issued. */
    public static List<Problem> employeeProblems(String subject, String firstName, String lastName, String idNumber, String taxNumber,
                                                 LocalDate today, Totals t, List<Line> lines) {
        return employeeProblems(subject, firstName, lastName, idNumber, null, null, taxNumber, today, t, lines);
    }

    /** As above, for an employee who may be identified by a passport instead of a South African ID. */
    public static List<Problem> employeeProblems(String subject, String firstName, String lastName, String idNumber, String passportNumber,
                                                 String passportCountry, String taxNumber, LocalDate today, Totals t, List<Line> lines) {
        List<Problem> out = new ArrayList<>();
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank())
            out.add(error(subject, "The employee's first names and surname are needed on the certificate."));
        String idProblem = EmployeeRules.idProblem(idNumber, today);
        boolean hasId = idNumber != null && !idNumber.isBlank();
        boolean hasPassport = EmployeeRules.normalisePassport(passportNumber) != null && EmployeeRules.normaliseCountry(passportCountry) != null;
        if (idProblem != null) out.add(hasPassport ? warning(subject, idProblem + " The passport is used on the certificate instead.") : error(subject, idProblem));
        if (!hasId && !hasPassport) out.add(error(subject, "No ID number or passport is recorded. SARS needs one of them to match the certificate to the employee."));
        String taxProblem = EmployeeRules.taxNumberProblem(taxNumber);
        if (taxProblem != null) out.add(error(subject, taxProblem));
        if (taxNumber == null || taxNumber.isBlank()) out.add(warning(subject, "No income tax reference number is recorded."));
        if (!hasIncome(lines)) out.add(error(subject, "Nothing is reported as income, but a certificate needs at least one income code."));
        if (t.paye().compareTo(t.basic().add(t.bonus()).add(t.overtime()).add(t.otherEarnings()).add(t.travel())) > 0)
            out.add(error(subject, "The tax deducted is more than the income paid, so the figures need to be checked before a certificate is issued."));
        if (t.payslipsWithoutCredit() > 0)
            out.add(warning(subject, t.payslipsWithoutCredit() + " payslip(s) were made before the medical tax credit was recorded, so code 4116 may be understated."));
        return out;
    }

    // ── Reconciliation ──────────────────────────────────────────────────────────────────────────

    /** One tax type compared three ways: payslips, certificates and the EMP201 returns. */
    public record ReconLine(String tax, BigDecimal payslips, BigDecimal certificates, BigDecimal emp201, BigDecimal difference, boolean balanced) {}

    public static ReconLine reconLine(String tax, BigDecimal payslips, BigDecimal certificates, BigDecimal emp201) {
        BigDecimal a = cents(payslips), b = cents(certificates), c = cents(emp201);
        BigDecimal diff = a.max(b).max(c).subtract(a.min(b).min(c));
        return new ReconLine(tax, a, b, c, diff, diff.signum() == 0);
    }

    // ── Settings ────────────────────────────────────────────────────────────────────────────────

    public static boolean isFundType(String t) { return PENSION.equals(t) || PROVIDENT.equals(t) || RETIREMENT_ANNUITY.equals(t); }

    public static String settingsProblem(String payeRef, String retirementFund) {
        if (payeRef != null && !payeRef.isBlank() && !payeRef.replaceAll("\\s", "").matches("\\d{10}"))
            return "The PAYE reference number is 10 digits.";
        if (!isFundType(retirementFund)) return "Choose pension, provident or retirement annuity.";
        return null;
    }
}
