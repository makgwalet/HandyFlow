package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** The generic bank payment file: one line per employee to pay, net pay into their bank account. */
public final class BankFileRules {

    /** What one employee's payment needs. */
    public record Payment(String employeeNumber, String employeeName, String bankName, String branchCode,
                          String accountNumber, BigDecimal amount, String reference) {}

    /** The file and what was left out of it, and why. */
    public record Result(String csv, int paymentCount, BigDecimal total, List<String> excluded) {}

    public static final String HEADER = "Employee number,Beneficiary name,Bank,Branch code,Account number,Amount,Reference";

    private BankFileRules() {}

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    public static Result build(List<Payment> payments) {
        StringBuilder sb = new StringBuilder(HEADER).append("\r\n");
        List<String> excluded = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (Payment p : payments) {
            if (p.amount() == null || p.amount().signum() <= 0) {
                excluded.add(p.employeeName() + ": nothing to pay.");
                continue;
            }
            if (blank(p.accountNumber())) {
                excluded.add(p.employeeName() + ": no bank account on record.");
                continue;
            }
            sb.append(CsvRules.row(List.of(CsvRules.cell(p.employeeNumber()), CsvRules.cell(p.employeeName()),
                    CsvRules.cell(p.bankName()), CsvRules.cell(p.branchCode()), CsvRules.cell(p.accountNumber().trim()),
                    CsvRules.money(p.amount()), CsvRules.cell(p.reference()))));
            total = total.add(p.amount());
            count++;
        }
        return new Result(sb.toString(), count, total, excluded);
    }

    /** A second file for a run needs a reason, because the first may already have been paid. */
    public static String supersedeProblem(boolean activeFileExists, String reason) {
        if (activeFileExists && (reason == null || reason.isBlank())) {
            return "A bank file was already made for this pay run. Say why a new one is needed; the earlier one will be marked replaced.";
        }
        if (reason != null && reason.trim().length() > 500) return "The reason is at most 500 characters.";
        return null;
    }
}
