package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The layouts the bank payment file can take. Pure, so it runs without a database.
 * <ul>
 *   <li><b>GENERIC_CSV</b>: name, bank, branch, account, amount, reference. See {@link PayrollPaymentRules#csv}.</li>
 *   <li><b>BANKSERV_ACB</b>: the fixed-width BankServ (ACB) import that FNB Online Banking documents as "Payment Bankserv File Format":
 *       records of 180 characters then CR LF; an installation header (02), a user header (04), one transaction (10) per payment, a contra (12)
 *       naming the account the money comes from and the action date, a user trailer (92) and an installation trailer (94). Amounts are in
 *       cents, names are cut to 15 characters and references to 20. Other banks' own layouts are not built.</li>
 * </ul>
 * The order of the contra among the transactions is not stated clearly in the bank's document (it lists it before the transactions but says
 * the user trailer "must follow a contra record"); it is written after them, as in BankServ practice. Try a small file first: the bank checks
 * an import and reports errors before anything is paid.
 */
public final class BankFileRules {

    private BankFileRules() {}

    public static final String GENERIC_CSV = "GENERIC_CSV", BANKSERV_ACB = "BANKSERV_ACB", ABSA_BIO = "ABSA_BIO";
    public static final List<String> LAYOUTS = List.of(GENERIC_CSV, BANKSERV_ACB, ABSA_BIO);
    public static final int RECORD_LENGTH = 180, NAME_LENGTH = 15, REFERENCE_LENGTH = 20, STANDARD_ACCOUNT_LENGTH = 11;
    /** BankServ account type codes: 1 current or cheque, 2 savings, 3 transmission, 4 bond. */
    public static char typeCode(String type) {
        return switch (type == null ? "" : type) { case PayrollPaymentRules.SAVINGS -> '2'; case PayrollPaymentRules.TRANSMISSION -> '3'; case PayrollPaymentRules.BOND -> '4'; default -> '1'; };
    }
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    public static String label(String layout) {
        return switch (layout == null ? "" : layout) {
            case BANKSERV_ACB -> "BankServ (ACB) fixed-width, as FNB Online Banking imports (Nedbank and Capitec list it too)";
            case ABSA_BIO -> "ABSA Business Integrator CSV (account, name, branch, amount, description; no headings)";
            default -> "Generic CSV (map the columns at your bank)";
        };
    }

    public static String filename(String layout, String payRunNumber) {
        String base = "salary-payments-" + (payRunNumber == null ? "run" : payRunNumber.replaceAll("[^A-Za-z0-9-]", ""));
        return base + (BANKSERV_ACB.equals(layout) ? ".txt" : ".csv");   // ABSA_BIO is a CSV too
    }

    /** Null when the layout can be used. BankServ needs the company's own account. */
    public static String layoutProblem(String layout, String nominatedBranch, String nominatedAccount) {
        if (layout == null || !LAYOUTS.contains(layout)) return "Choose a file layout.";
        if (!BANKSERV_ACB.equals(layout)) return null;           // only BankServ names the company's own account
        return nominatedProblem(nominatedBranch, nominatedAccount) == null ? null
                : "The BankServ layout needs the company's own bank account. Enter it under Payroll settings. (" + nominatedProblem(nominatedBranch, nominatedAccount) + ")";
    }

    /** Null when the company's account can be written to a BankServ file. */
    public static String nominatedProblem(String branch, String account) {
        String b = PayrollPaymentRules.digits(branch), a = PayrollPaymentRules.digits(account);
        if (b.isEmpty() && a.isEmpty()) return "no branch code or account number";
        if (!b.matches("\\d{6}")) return "the branch code must be 6 digits";
        if (!a.matches("\\d{1,11}")) return "the account number must be 1 to 11 digits";
        return null;
    }

    /** The day the bank pays: the pay date, but never a day that has gone. */
    public static LocalDate actionDate(LocalDate payDate, LocalDate today) {
        return payDate == null || payDate.isBefore(today) ? today : payDate;
    }

    /** Upper-case ASCII letters, digits and spaces only; accents are dropped to their base letter. Cut to {@code max}. */
    public static String plain(String s, int max) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    /** The whole file. Lines are in the order given and numbered from 000001. */
    public static String bankserv(List<PayrollPaymentRules.Line> lines, String nominatedBranch, String nominatedAccount, LocalDate actionDate) {
        String problem = nominatedProblem(nominatedBranch, nominatedAccount);
        if (problem != null) throw new IllegalArgumentException("The company's bank account: " + problem + ".");
        if (lines.isEmpty()) throw new IllegalArgumentException("There are no payments to write.");
        String ub = PayrollPaymentRules.digits(nominatedBranch), ua = zeros(PayrollPaymentRules.digits(nominatedAccount), 11);
        StringBuilder sb = new StringBuilder();
        sb.append(rec("02" + spaces(178)));
        sb.append(rec("04" + spaces(178)));
        int seq = 0;
        for (PayrollPaymentRules.Line l : lines) {
            seq++;
            String branch = PayrollPaymentRules.digits(l.branch()), account = PayrollPaymentRules.digits(l.account());
            if (!branch.matches("\\d{6}") || !account.matches("\\d{1,20}")) throw new IllegalArgumentException("Bad bank details for " + plain(l.name(), 40) + ".");
            long cents = PayrollPaymentRules.money(l.amount()).movePointRight(2).longValueExact();
            if (cents <= 0 || cents > 99_999_999_999L) throw new IllegalArgumentException("The amount for " + plain(l.name(), 40) + " cannot be written to the file.");
            boolean standard = account.length() <= STANDARD_ACCOUNT_LENGTH;
            sb.append(rec("10" + ub + ua + "0000" + zeros(String.valueOf(seq), 6) + branch
                    + (standard ? zeros(account, 11) : zeros("", 11)) + typeCode(l.accountType()) + zeros(String.valueOf(cents), 11)
                    + zeros("", 8) + "0" + "00" + "0" + pad(plain(l.reference(), REFERENCE_LENGTH), REFERENCE_LENGTH) + spaces(10)
                    + pad(plain(l.name(), NAME_LENGTH), NAME_LENGTH) + spaces(15) + (standard ? zeros("", 20) : zeros(account, 20)) + spaces(30)));
        }
        sb.append(rec("12" + ub + ua + spaces(39) + actionDate.format(YYMMDD) + spaces(116)));
        sb.append(rec("92" + spaces(178)));
        sb.append(rec("94" + spaces(178)));
        return sb.toString();
    }

    /**
     * The ABSA Business Integrator Online import: one line per payment, no headings, five columns in this order: to account number, to account
     * name, to branch code, amount, beneficiary description (what the person sees on their statement). The layout is as ABSA's own import screen
     * maps it according to payroll vendors' guides; the bank's field lengths are not published where I could read them, so names and descriptions
     * are cut to the same lengths as the BankServ file. Amounts are rand with two decimals.
     */
    public static String absaBio(List<PayrollPaymentRules.Line> lines) {
        if (lines.isEmpty()) throw new IllegalArgumentException("There are no payments to write.");
        StringBuilder sb = new StringBuilder();
        for (PayrollPaymentRules.Line l : lines) {
            String branch = PayrollPaymentRules.digits(l.branch()), account = PayrollPaymentRules.digits(l.account());
            if (!branch.matches("\\d{6}") || !account.matches("\\d{6,16}")) throw new IllegalArgumentException("Bad bank details for " + plain(l.name(), 40) + ".");
            BigDecimal amount = PayrollPaymentRules.money(l.amount());
            if (amount.signum() <= 0) throw new IllegalArgumentException("The amount for " + plain(l.name(), 40) + " cannot be written to the file.");
            sb.append(account).append(',').append(plain(l.name(), 30)).append(',').append(branch).append(',').append(amount.toPlainString()).append(',')
              .append(plain(l.reference(), REFERENCE_LENGTH)).append("\r\n");
        }
        return sb.toString();
    }

    /** Sum of what the file pays, for the person checking it against the bank's own total. */
    public static BigDecimal total(List<PayrollPaymentRules.Line> lines) {
        return lines.stream().map(l -> PayrollPaymentRules.money(l.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String rec(String body) {
        if (body.length() != RECORD_LENGTH) throw new IllegalStateException("A record is " + body.length() + " characters, not " + RECORD_LENGTH + ".");
        return body + "\r\n";
    }

    private static String spaces(int n) { return " ".repeat(n); }
    private static String zeros(String digits, int n) { return "0".repeat(Math.max(0, n - digits.length())) + digits; }
    private static String pad(String s, int n) { return s + " ".repeat(Math.max(0, n - s.length())); }
}
