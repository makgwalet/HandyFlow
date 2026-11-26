package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * IRP5/IT3(a) certificate data for one employee and the EMP501 reconciliation of all of them to the EMP201 figures (phase 4).
 *
 * <p>This builds the figures and checks them. It does NOT produce SARS's tax-certificate CSV: that layout is in SARS's
 * PAYE Employer Reconciliation BRS, which changes each season and has not been read for this build.
 *
 * <p>Code facts below were read from SARS's 2027 code guides (PAYE-AE-06-G06, G07) and the employer reconciliation guide
 * (EMP-GEN-02-G01) on 2026-11-26: 3601 salary and wages, 3602 non-taxable income, 3606 commission, 3607 overtime (valid
 * from 2020), 3701 travel allowance, 3605 not valid for the 2027 year, 3696 total non-taxable, 3699 gross taxable, 4102 PAYE,
 * 4141 UIF (employee and employer), 4142 SDL, 4149 sum of 4101, 4102, 4115, 4141, 4142; IRP5 when 4101, 4102 or 4115 is above
 * zero, otherwise IT3(a) with a reason in 4150. Codes not in that list are accepted but flagged as unchecked.
 */
public final class TaxCertificateRules {

    public static final String BLOCK = "BLOCK";
    public static final String WARN = "WARN";

    /** Codes whose meaning and validity for the 2027 year were confirmed in SARS's guides. */
    public static final Set<String> CONFIRMED_INCOME = Set.of("3601", "3602", "3606", "3607", "3701");
    /** Income codes that go into the non-taxable total 3696. */
    public static final Set<String> NON_TAXABLE = Set.of("3602", "3652");
    /** Codes SARS's 2027 list says are not valid for the year. */
    public static final Map<String, String> NOT_VALID_2027 = Map.of("3605", "SARS's 2027 code list says 3605 applied from 2003 to 2018 only");
    /** Deduction codes used for the pension and medical amounts the bureau records. From memory of SARS's list; not confirmed in this build. */
    public static final String PENSION_CODE = "4001";
    public static final String MEDICAL_CODE = "4005";

    public static final String IRP5 = "IRP5";
    public static final String IT3A = "IT3(a)";

    public record Amount(String code, BigDecimal amount) {}
    public record Finding(String severity, String code, String message) {}
    public record Line(String code, BigDecimal amount) {}

    public record Input(String employeeName, String idNumber, String taxNumber, List<Amount> income,
                        BigDecimal pension, BigDecimal medical, BigDecimal paye, BigDecimal uifTotal, BigDecimal sdl) {}

    public record Certificate(String employeeName, String type, List<Line> income, List<Line> deductions,
                              BigDecimal nonTaxable3696, BigDecimal grossTaxable3699, BigDecimal paye4102,
                              BigDecimal uif4141, BigDecimal sdl4142, BigDecimal total4149, List<Finding> findings) {
        public boolean blocked() { return findings.stream().anyMatch(f -> BLOCK.equals(f.severity())); }
    }

    private TaxCertificateRules() {}

    private static BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    public static Certificate build(Input in) {
        List<Finding> findings = new ArrayList<>();
        Map<String, BigDecimal> byCode = new TreeMap<>();
        for (Amount a : in.income()) {
            if (a.amount() == null || a.amount().signum() == 0) continue;
            byCode.merge(a.code(), a.amount(), BigDecimal::add);
        }
        BigDecimal nonTaxable = BigDecimal.ZERO, taxable = BigDecimal.ZERO;
        List<Line> income = new ArrayList<>();
        for (var e : byCode.entrySet()) {
            income.add(new Line(e.getKey(), money(e.getValue())));
            if (NON_TAXABLE.contains(e.getKey())) nonTaxable = nonTaxable.add(e.getValue()); else taxable = taxable.add(e.getValue());
            if (NOT_VALID_2027.containsKey(e.getKey())) {
                findings.add(new Finding(BLOCK, "CODE_NOT_VALID", "Code " + e.getKey() + " cannot be used on a 2027 certificate: " + NOT_VALID_2027.get(e.getKey())
                        + ". Change the pay item's SARS code."));
            } else if (!CONFIRMED_INCOME.contains(e.getKey())) {
                findings.add(new Finding(WARN, "CODE_UNCHECKED", "Code " + e.getKey() + " was not checked against SARS's 2027 code list."));
            }
        }
        if (income.isEmpty()) findings.add(new Finding(BLOCK, "NO_INCOME", "There is no income for this employee in the period."));

        List<Line> deductions = new ArrayList<>();
        if (z(in.pension()).signum() > 0) deductions.add(new Line(PENSION_CODE, money(in.pension())));
        if (z(in.medical()).signum() > 0) deductions.add(new Line(MEDICAL_CODE, money(in.medical())));
        if (!deductions.isEmpty()) {
            findings.add(new Finding(WARN, "DEDUCTION_CODES_UNCHECKED",
                    "Pension (" + PENSION_CODE + ") and medical aid (" + MEDICAL_CODE + ") codes were not checked against SARS's list, and the fund type is not recorded."));
        }

        BigDecimal paye = money(z(in.paye())), uif = money(z(in.uifTotal())), sdl = money(z(in.sdl()));
        BigDecimal total = paye.add(uif).add(sdl);
        String type = paye.signum() > 0 ? IRP5 : IT3A;
        if (IT3A.equals(type)) {
            findings.add(new Finding(WARN, "REASON_4150_NEEDED", "No PAYE was deducted, so this is an IT3(a) and SARS requires a reason for non-deduction (code 4150), which is not recorded."));
        }
        if (in.taxNumber() == null || in.taxNumber().isBlank()) {
            findings.add(new Finding(BLOCK, "NO_TAX_NUMBER", "No income tax reference number. SARS has refused reconciliations without valid tax numbers since February 2026."));
        }
        if (in.idNumber() == null || in.idNumber().isBlank()) {
            findings.add(new Finding(WARN, "NO_ID_NUMBER", "No identity or passport number."));
        }
        if (paye.compareTo(taxable) > 0) {
            findings.add(new Finding(BLOCK, "PAYE_EXCEEDS_INCOME", "PAYE (4102) is more than gross taxable income (3699); SARS refuses this unless it is a voluntary over-deduction."));
        }
        return new Certificate(in.employeeName(), type, income, deductions, money(nonTaxable), money(taxable), paye, uif, sdl, total, findings);
    }

    // ── EMP501 reconciliation ───────────────────────────────────────────────

    /** One month of EMP201 figures. */
    public record Monthly(String month, String status, BigDecimal paye, BigDecimal uif, BigDecimal sdl,
                          BigDecimal etiUsed, BigDecimal etiCarriedForward) {}

    public record Difference(String what, BigDecimal emp201, BigDecimal certificates, BigDecimal difference) {}

    public record Reconciliation(List<Difference> differences, BigDecimal etiUsed, BigDecimal etiUnusedAtEnd,
                                 List<Finding> findings) {
        public boolean balanced() { return differences.stream().allMatch(d -> d.difference().signum() == 0); }
    }

    /**
     * Compares the EMP201 figures for the period with the certificate totals. {@code expectedMonths} are the months that have
     * finalised pay runs in them: each needs a prepared EMP201, and an unfiled one is flagged.
     */
    public static Reconciliation reconcile(List<Monthly> emp201, List<String> expectedMonths, List<Certificate> certificates) {
        List<Finding> findings = new ArrayList<>();
        Set<String> have = new HashSet<>();
        BigDecimal paye = BigDecimal.ZERO, uif = BigDecimal.ZERO, sdl = BigDecimal.ZERO, eti = BigDecimal.ZERO;
        BigDecimal lastCarried = BigDecimal.ZERO;
        for (Monthly m : emp201) {
            have.add(m.month());
            paye = paye.add(m.paye()); uif = uif.add(m.uif()); sdl = sdl.add(m.sdl());
            eti = eti.add(m.etiUsed()); lastCarried = m.etiCarriedForward();
            if (!"FILED".equals(m.status())) {
                findings.add(new Finding(WARN, "EMP201_NOT_FILED", "The EMP201 for " + m.month() + " is not marked as filed."));
            }
        }
        for (String m : expectedMonths) {
            if (!have.contains(m)) {
                findings.add(new Finding(BLOCK, "EMP201_MISSING", "There are finalised pay runs in " + m + " but no EMP201 has been prepared for it."));
            }
        }
        BigDecimal cPaye = BigDecimal.ZERO, cUif = BigDecimal.ZERO, cSdl = BigDecimal.ZERO;
        for (Certificate c : certificates) {
            cPaye = cPaye.add(c.paye4102()); cUif = cUif.add(c.uif4141()); cSdl = cSdl.add(c.sdl4142());
        }
        List<Difference> diffs = List.of(
                diff("PAYE", paye, cPaye), diff("UIF", uif, cUif), diff("SDL", sdl, cSdl));
        for (Difference d : diffs) {
            if (d.difference().signum() != 0) {
                findings.add(new Finding(WARN, "DIFFERENCE_" + d.what().toUpperCase(),
                        d.what() + " differs by " + d.difference().toPlainString() + " between the EMP201 returns and the certificates; SARS asks for a reason on the EMP501."));
            }
        }
        return new Reconciliation(diffs, money(eti), money(lastCarried), findings);
    }

    private static Difference diff(String what, BigDecimal emp201, BigDecimal certs) {
        return new Difference(what, money(emp201), money(certs), money(emp201.subtract(certs)));
    }
}
