package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The payroll journal of one pay run, for the client's own accounting package. The account names are suggestions the client maps to
 * its own chart of accounts. A reversed run gives the same journal with debits and credits swapped.
 */
public final class JournalRules {

    /** The run's totals. {@code totalEarnings} includes salary, travel, taxable extras, bonus and non-taxable earnings. */
    public record Totals(BigDecimal totalEarnings, BigDecimal paye, BigDecimal uifEmployee, BigDecimal uifEmployer, BigDecimal sdl,
                         BigDecimal medicalAid, BigDecimal pension, BigDecimal otherDeductions, BigDecimal netPay) {}

    public record Line(String account, BigDecimal debit, BigDecimal credit) {}

    private JournalRules() {}

    private static void add(List<Line> out, String account, BigDecimal debit, BigDecimal credit) {
        if (debit.signum() == 0 && credit.signum() == 0) return;
        out.add(new Line(account, debit, credit));
    }

    public static List<Line> lines(Totals t, boolean reversal) {
        BigDecimal z = BigDecimal.ZERO;
        List<Line> out = new ArrayList<>();
        add(out, "Salaries and wages (expense)", t.totalEarnings(), z);
        add(out, "Employer UIF contribution (expense)", t.uifEmployer(), z);
        add(out, "Skills development levy (expense)", t.sdl(), z);
        add(out, "PAYE payable (liability)", z, t.paye());
        add(out, "UIF payable (liability)", z, t.uifEmployee().add(t.uifEmployer()));
        add(out, "SDL payable (liability)", z, t.sdl());
        add(out, "Medical aid payable (liability)", z, t.medicalAid());
        add(out, "Retirement fund payable (liability)", z, t.pension());
        add(out, "Other deductions payable (liability)", z, t.otherDeductions());
        add(out, "Net salaries payable (liability)", z, t.netPay());
        if (!reversal) return out;
        List<Line> swapped = new ArrayList<>();
        for (Line l : out) swapped.add(new Line(l.account(), l.credit(), l.debit()));
        return swapped;
    }

    public static boolean balanced(List<Line> lines) {
        BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
        for (Line l : lines) { dr = dr.add(l.debit()); cr = cr.add(l.credit()); }
        return dr.compareTo(cr) == 0;
    }

    public static String csv(String runNumber, String period, String payDate, boolean reversal, List<Line> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvRules.row(List.of("Pay run", CsvRules.cell(runNumber))));
        sb.append(CsvRules.row(List.of("Period", CsvRules.cell(period))));
        sb.append(CsvRules.row(List.of("Pay date", CsvRules.cell(payDate))));
        if (reversal) sb.append(CsvRules.row(List.of("Note", "Reversal of this pay run: debits and credits are swapped")));
        sb.append("Account,Debit,Credit\r\n");
        for (Line l : lines) {
            sb.append(CsvRules.row(List.of(CsvRules.cell(l.account()), CsvRules.money(l.debit()), CsvRules.money(l.credit()))));
        }
        return sb.toString();
    }
}
