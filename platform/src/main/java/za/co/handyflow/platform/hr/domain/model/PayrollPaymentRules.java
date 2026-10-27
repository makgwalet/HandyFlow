package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The rules for paying a finalised pay run by bank file. Pure, so it runs without a database.
 * <p>
 * The file is a plain CSV (name, bank, branch code, account, amount, reference) that most business banking sites can import after
 * a column mapping. It is not any one bank's own layout.
 */
public final class PayrollPaymentRules {

    public static final String PENDING = "PENDING", PAID = "PAID", RETURNED = "RETURNED";
    public static final int MAX_REFERENCE = 20, MAX_REASON = 300;

    private PayrollPaymentRules() {}

    /** One line of the file. */
    public record Line(String name, String bank, String branch, String account, BigDecimal amount, String reference) {}

    /** Spaces and dashes are allowed when bank details are typed in; the file carries digits only. */
    public static String digits(String s) {
        return s == null ? "" : s.replaceAll("[\\s-]", "");
    }

    /** @return what is wrong with the bank details, or null when they can go in a file */
    public static String bankProblem(String name, String account, String branch) {
        if (name == null || name.isBlank()) return "no name to pay";
        String a = digits(account), b = digits(branch);
        if (a.isEmpty()) return "no bank account number";
        if (!a.matches("\\d{6,16}")) return "the account number must be 6 to 16 digits";
        if (b.isEmpty()) return "no branch code";
        if (!b.matches("\\d{6}")) return "the branch code must be 6 digits";
        return null;
    }

    /** What the employee sees on their statement: letters, digits and spaces only, at most 20 characters. */
    public static String reference(String payRunNumber, String employeeNumber) {
        String s = ("SAL " + (payRunNumber == null ? "" : payRunNumber) + " " + (employeeNumber == null ? "" : employeeNumber))
                .replaceAll("[^A-Za-z0-9 ]", "").trim();
        return s.length() > MAX_REFERENCE ? s.substring(0, MAX_REFERENCE).trim() : s;
    }

    public static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** @param runStatus the pay run's status; @param nonPending payments already paid or returned */
    public static String generateProblem(String runStatus, long nonPending) {
        if (!PayRunWorkflowRules.isIssued(runStatus))
            return "Payments can be made up once the pay run has been finalised. This run is " + runStatus + ".";
        if (nonPending > 0)
            return "Some payments for this run have already been paid or returned, so the file cannot be made up again.";
        return null;
    }

    public static String markPaidProblem(long pending, long notDownloaded) {
        if (pending == 0) return "There are no pending payments to confirm.";
        if (notDownloaded > 0) return "Download the payment file before confirming that it was paid.";
        return null;
    }

    public static String returnProblem(String status, String reason) {
        if (!PENDING.equals(status) && !PAID.equals(status)) return "This payment is already " + status.toLowerCase() + ".";
        if (reason == null || reason.isBlank()) return "Say why the bank returned it.";
        if (reason.trim().length() > MAX_REASON) return "The reason is too long.";
        return null;
    }

    public static String filename(String payRunNumber) {
        return "salary-payments-" + (payRunNumber == null ? "run" : payRunNumber.replaceAll("[^A-Za-z0-9-]", "")) + ".csv";
    }

    /** Byte order mark first so Excel reads it as UTF-8; names starting with a formula character are neutralised. */
    public static String csv(List<Line> lines) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Beneficiary name,Bank,Branch code,Account number,Amount,Reference\r\n");
        for (Line l : lines) {
            sb.append(cell(l.name())).append(',').append(cell(l.bank())).append(',').append(cell(digits(l.branch()))).append(',')
              .append(cell(digits(l.account()))).append(',').append(money(l.amount()).toPlainString()).append(',')
              .append(cell(l.reference())).append("\r\n");
        }
        return sb.toString();
    }

    static String cell(String v) {
        if (v == null) return "";
        String s = v.replace("\r", " ").replace("\n", " ").trim();
        if (!s.isEmpty() && "=+-@\t".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
