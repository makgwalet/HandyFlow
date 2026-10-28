package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The file e@syFile imports for an employer reconciliation (EMP501), in the code,value layout of the SARS PAYE Employer Reconciliation BRS:
 * an employer record, one record per tax certificate, and an employer totals record. Each record is one line of code,value pairs ending in 9999.
 * Text is quoted; Rand amounts are whole Rand rounded down, except the tax codes, which keep their cents.
 * Built from the BRS as far as it could be read (the totals record from an older version of it), so SARS's own validation on upload has the last word.
 */
public final class EasyFileRules {

    private EasyFileRules() {}

    public record Address(String street, String suburb, String city, String postalCode) {}

    public record Employer(String tradingName, String payeRef, String sdlRef, String uifRef, String contactFirstName, String contactSurname,
                           String contactPhone, String contactEmail, String sic7, Address address, int taxYear, String period, boolean live) {}

    public record Person(String certificateNumber, String type, String surname, String firstNames, String idNumber, String taxNumber, LocalDate dateOfBirth,
                         String employeeNumber, String email, String phone, Address address, LocalDate from, LocalDate to,
                         BigDecimal payPeriods, BigDecimal periodsWorked, List<TaxCertificateRules.Line> lines,
                         String passportNumber, String passportCountry, LocalDate employedFrom, List<EtiRules.Month> eti) {
        /** Without employment tax incentive information, as the person was before it was written. */
        public Person(String certificateNumber, String type, String surname, String firstNames, String idNumber, String taxNumber, LocalDate dateOfBirth,
                      String employeeNumber, String email, String phone, Address address, LocalDate from, LocalDate to,
                      BigDecimal payPeriods, BigDecimal periodsWorked, List<TaxCertificateRules.Line> lines,
                      String passportNumber, String passportCountry) {
            this(certificateNumber, type, surname, firstNames, idNumber, taxNumber, dateOfBirth, employeeNumber, email, phone, address, from, to,
                    payPeriods, periodsWorked, lines, passportNumber, passportCountry, null, List.of());
        }

        /** Without a passport, as the person was before passports were kept. */
        public Person(String certificateNumber, String type, String surname, String firstNames, String idNumber, String taxNumber, LocalDate dateOfBirth,
                      String employeeNumber, String email, String phone, Address address, LocalDate from, LocalDate to,
                      BigDecimal payPeriods, BigDecimal periodsWorked, List<TaxCertificateRules.Line> lines) {
            this(certificateNumber, type, surname, firstNames, idNumber, taxNumber, dateOfBirth, employeeNumber, email, phone, address, from, to,
                    payPeriods, periodsWorked, lines, null, null, null, List.of());
        }

        /** The 13-digit ID goes in the file when there is one; otherwise the passport does. */
        boolean usesId() { return idNumber != null && idNumber.matches("\\d{13}"); }
        boolean hasEti() { return eti != null && !eti.isEmpty(); }
        boolean hasPassport() { return !blank(passportNumber) && !blank(passportCountry); }
        String label() { return (firstNames == null ? "" : firstNames + " ") + (surname == null ? "" : surname) + (employeeNumber == null ? "" : " (" + employeeNumber + ")"); }
    }

    public static final String SOFTWARE_PROVIDER = "HandyFlow", SOFTWARE_PACKAGE = "HandyFlow HR";

    /** Codes whose amount keeps its cents; every other Rand amount is whole Rand rounded down. */
    private static final java.util.Set<String> WITH_CENTS = java.util.Set.of("4101", "4102", "4115", "4116", "4118", "4120", "4141", "4142", "4149",
            "7002", "7003", "7004", "7008");

    /** The codes that make up 4149, total tax, SDL and UIF. */
    private static final java.util.Set<String> TOTALLED = java.util.Set.of("4101", "4102", "4115", "4141", "4142");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── Checks ──────────────────────────────────────────────────────────────────────────────────

    /** What stops the file being made. Each entry says what is missing and where it is kept. */
    public static List<String> problems(Employer e, List<Person> people) {
        List<String> out = new ArrayList<>();
        if (blank(e.tradingName())) out.add("Employer: the company name is not set.");
        if (e.payeRef() == null || !e.payeRef().matches("7\\d{9}")) out.add("Employer: the PAYE reference must be 10 digits and start with 7 (Tax settings).");
        if (blank(e.contactFirstName()) || blank(e.contactSurname())) out.add("Employer: the contact person's first name and surname are not set (Tax settings).");
        if (digits(e.contactPhone()).isEmpty()) out.add("Employer: the company has no telephone number (company settings).");
        if (e.sic7() == null || !e.sic7().matches("[0-9A-Za-z]{5}")) out.add("Employer: the SIC7 code must be 5 characters (Tax settings).");
        if (e.address() == null || blank(e.address().street())) out.add("Employer: the company has no street address (company settings).");
        if (e.address() == null || e.address().postalCode() == null || !e.address().postalCode().trim().matches("\\d{4}"))
            out.add("Employer: the company's postal code must be four digits (company settings).");
        if (people.isEmpty()) out.add("There are no issued certificates for this period.");
        for (Person p : people) {
            String who = p.label() + ": ";
            for (EtiRules.Month m : p.eti() == null ? List.<EtiRules.Month>of() : p.eti())
                if (m.cycle() > 0 && m.hours() < 1) out.add(who + "the employment tax incentive for month " + m.calendarMonth() + " has no hours.");
            if (p.hasEti() && p.employedFrom() == null) out.add(who + "the employment tax incentive needs the date the person started work.");
            if (!p.usesId() && !p.hasPassport())
                out.add(who + "a 13-digit ID number, or a passport number with its country, is needed.");
            else if (p.usesId() && p.dateOfBirth() != null && !p.idNumber().startsWith(String.format("%02d%02d%02d", p.dateOfBirth().getYear() % 100, p.dateOfBirth().getMonthValue(), p.dateOfBirth().getDayOfMonth())))
                out.add(who + "the ID number does not match the date of birth.");
            if (p.dateOfBirth() == null) out.add(who + "the date of birth is not set.");
            if (blank(p.surname()) || blank(p.firstNames())) out.add(who + "the first names and surname are needed.");
            if (p.address() == null || blank(p.address().street())) out.add(who + "the residential street address is not set.");
            else if (p.address().postalCode() == null || !p.address().postalCode().trim().matches("\\d{4}")) out.add(who + "the residential postal code must be four digits.");
            if (p.certificateNumber() == null || p.certificateNumber().length() != 30) out.add(who + "the certificate number is not 30 characters.");
        }
        return out;
    }

    // ── The file ────────────────────────────────────────────────────────────────────────────────

    public static String file(Employer e, List<Person> people) {
        List<List<String[]>> records = new ArrayList<>();
        records.add(employerRecord(e));
        for (Person p : people) records.add(personRecord(e, p));

        long codeTotal = 0;
        BigDecimal amountTotal = BigDecimal.ZERO;
        for (List<String[]> r : records) {
            for (String[] pair : r) {
                codeTotal += Integer.parseInt(pair[0]);
                int c = Integer.parseInt(pair[0]);
                if (c >= 3601 && c <= 4497 && c != 4150 || c >= 7002 && c <= 7004) amountTotal = amountTotal.add(new BigDecimal(pair[1]));
            }
        }
        List<String[]> trailer = new ArrayList<>();
        trailer.add(new String[]{"6010", String.valueOf(records.size())});
        trailer.add(new String[]{"6020", String.valueOf(codeTotal)});
        trailer.add(new String[]{"6030", amountTotal.setScale(2, RoundingMode.DOWN).toPlainString()});
        trailer.add(new String[]{"9999", "9999"});

        StringBuilder sb = new StringBuilder();
        for (List<String[]> r : records) sb.append(line(r)).append("\r\n");
        sb.append(line(trailer)).append("\r\n");
        return sb.toString();
    }

    private static String line(List<String[]> pairs) {
        StringBuilder sb = new StringBuilder();
        for (String[] p : pairs) {
            if ("9999".equals(p[0])) { sb.append("9999"); break; }
            sb.append(p[0]).append(',').append(p[1]).append(',');
        }
        return sb.toString();
    }

    private static List<String[]> employerRecord(Employer e) {
        Pairs p = new Pairs();
        p.text("2010", e.tradingName(), 90);
        p.text("2015", e.live() ? "LIVE" : "TEST", 4);
        p.num("2020", e.payeRef());
        if (!blank(e.sdlRef())) p.text("2022", e.sdlRef(), 10);
        if (!blank(e.uifRef())) p.text("2024", e.uifRef(), 10);
        p.text("2025", e.contactFirstName(), 50);
        p.text("2026", digits(e.contactPhone()), 15);
        if (!blank(e.contactEmail())) p.text("2027", e.contactEmail(), 70);
        p.text("2028", SOFTWARE_PROVIDER, 70);
        p.text("2029", SOFTWARE_PACKAGE, 70);
        p.num("2030", String.valueOf(e.taxYear()));
        p.num("2031", reconciliationMonth(e.taxYear(), e.period()));
        p.text("2036", e.contactSurname(), 50);
        p.text("2064", e.address().street(), 35);
        if (!blank(e.address().suburb())) p.text("2065", e.address().suburb(), 35);
        if (!blank(e.address().city())) p.text("2066", e.address().city(), 35);
        p.num("2080", e.address().postalCode().trim());
        p.text("2081", "ZA", 2);
        p.text("2082", e.sic7().toUpperCase(), 5);
        return p.done();
    }

    private static List<String[]> personRecord(Employer e, Person x) {
        Pairs p = new Pairs();
        p.text("3010", x.certificateNumber(), 30);
        p.text("3015", isIt3a(x.type()) ? "IT3(a)" : "IRP5", 6);
        p.text("3020", "A", 1);
        p.num("3025", String.valueOf(e.taxYear()));
        p.text("3026", x.hasEti() ? "Y" : "N", 1);
        p.text("3030", x.surname(), 120);
        p.text("3040", firstTwo(x.firstNames()), 90);
        p.text("3050", initials(x.firstNames()), 5);
        if (x.usesId()) p.num("3060", x.idNumber());
        else {
            p.text("3070", x.passportNumber().replaceAll("[\\s-]", "").toUpperCase(), 20);
            p.text("3075", x.passportCountry().trim().toUpperCase(), 2);
        }
        p.num("3080", x.dateOfBirth().format(DAY));
        if (x.taxNumber() != null && x.taxNumber().matches("\\d{10}")) p.num("3100", x.taxNumber());
        if (!blank(x.email())) p.text("3125", x.email(), 70);
        p.text("3136", digits(!digits(x.phone()).isEmpty() ? x.phone() : e.contactPhone()), 15);
        // The work address is the employer's.
        p.text("3147", e.address().street(), 35);
        if (!blank(e.address().suburb())) p.text("3148", e.address().suburb(), 35);
        if (!blank(e.address().city())) p.text("3149", e.address().city(), 35);
        p.num("3150", e.address().postalCode().trim());
        p.text("3151", "ZA", 2);
        if (!blank(x.employeeNumber())) p.text("3160", x.employeeNumber(), 25);
        p.num("3170", x.from().format(DAY));
        p.num("3180", x.to().format(DAY));
        if (x.hasEti() && x.employedFrom() != null) p.num("3190", x.employedFrom().format(DAY));
        p.text("3195", "N", 1);
        p.num("3200", x.payPeriods().setScale(4, RoundingMode.HALF_UP).toPlainString());
        p.num("3210", x.periodsWorked().setScale(4, RoundingMode.HALF_UP).toPlainString());
        p.text("3220", "N", 1);
        p.num("3240", "0");
        p.text("3263", e.sic7().toUpperCase(), 5);
        p.text("3279", "N", 1);
        p.text("3214", x.address().street(), 35);
        if (!blank(x.address().suburb())) p.text("3215", x.address().suburb(), 35);
        if (!blank(x.address().city())) p.text("3216", x.address().city(), 35);
        p.num("3217", x.address().postalCode().trim());
        p.text("3285", "ZA", 2);
        p.num("3288", "1");

        // Income and deductions, in code order. Totals the lines do not carry are worked out here.
        List<TaxCertificateRules.Line> lines = new ArrayList<>(x.lines());
        lines.sort(Comparator.comparing(TaxCertificateRules.Line::code));
        BigDecimal income = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        java.util.Set<String> have = new java.util.HashSet<>();
        for (TaxCertificateRules.Line l : lines) {
            // The totals and the reason are worked out here, from the whole-Rand amounts the file carries.
            if (TaxCertificateRules.DERIVED.contains(l.code())) continue;
            String amount = amount(l.code(), l.amount());
            if (isIncome(l.code())) income = income.add(new BigDecimal(amount));
            if (TOTALLED.contains(l.code())) tax = tax.add(new BigDecimal(amount));
            if (!WITH_CENTS.contains(l.code()) && new BigDecimal(amount).signum() == 0) continue;
            have.add(l.code());
            p.num(l.code(), amount);
        }
        // SARS wants the employee's own contribution code beside an employer contribution, even at nil.
        if (have.contains("3810") && !have.contains("4005")) p.num("4005", "0");
        if (have.contains("4472") && !have.contains("4001")) p.num("4001", "0");
        if (have.contains("4473") && !have.contains("4003")) p.num("4003", "0");
        if (have.contains("4475") && !have.contains("4006")) p.num("4006", "0");
        if (income.signum() > 0) p.num("3699", income.toPlainString());
        p.num("4149", tax.setScale(2, RoundingMode.DOWN).toPlainString());
        if (isIt3a(x.type()))
            p.num("4150", String.valueOf(TaxCertificateRules.reasonCode(lines.stream().filter(l -> "4116".equals(l.code())).map(TaxCertificateRules.Line::amount).findFirst().orElse(null))));
        // Employment tax incentive: one set of codes for each month, in code order, after the certificate's own codes.
        if (x.hasEti()) {
            for (EtiRules.Month m : x.eti()) {
                p.repeated("7002", cents(m.remuneration()));
                p.repeated("7003", cents(m.minimumWage()));
                p.repeated("7004", cents(m.eti()));
                p.repeated("7005", String.valueOf(m.cycle()));
                p.repeated("7006", String.format("%02d", m.calendarMonth()));
                p.repeated("7007", String.valueOf(m.hours()));
                p.repeated("7008", cents(m.wagePaid()));
            }
        }
        return p.done();
    }

    // ── Pieces ──────────────────────────────────────────────────────────────────────────────────

    private static String cents(BigDecimal v) { return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.DOWN).toPlainString(); }

    private static boolean isIt3a(String type) { return "IT3A".equals(type) || "IT3(a)".equals(type); }

    private static boolean isIncome(String code) { return TaxCertificateRules.isIncomeCode(code); }

    static String amount(String code, BigDecimal v) {
        BigDecimal a = v == null ? BigDecimal.ZERO : v;
        return WITH_CENTS.contains(code) ? a.setScale(2, RoundingMode.HALF_UP).toPlainString() : a.setScale(0, RoundingMode.DOWN).toPlainString();
    }

    /** The reconciliation month CCYYMM: August for an interim reconciliation, February for the annual one. */
    public static String reconciliationMonth(int taxYear, String period) {
        return TaxCertificateRules.INTERIM.equals(period) ? (taxYear - 1) + "08" : taxYear + "02";
    }

    static String firstTwo(String names) {
        String[] parts = clean(names, 90).split(" ");
        return parts.length <= 2 ? String.join(" ", parts) : parts[0] + " " + parts[1];
    }

    static String initials(String names) {
        StringBuilder sb = new StringBuilder();
        for (String part : clean(names, 200).split("[ -]+")) if (!part.isEmpty() && sb.length() < 5) sb.append(Character.toUpperCase(part.charAt(0)));
        return sb.toString();
    }

    /** Plain ASCII, no commas, pipes, quotes or doubled spaces, none leading; cut to the field's length. */
    static String clean(String v, int max) {
        if (v == null) return "";
        String s = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E]", " ")
                .replace(',', ' ').replace('|', ' ').replace('"', '\'').replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max).trim() : s;
    }

    static String digits(String v) {
        if (v == null) return "";
        String s = v.replaceAll("[^0-9+]", "");
        return s.length() > 15 ? s.substring(0, 15) : s;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static final class Pairs {
        private final List<String[]> out = new ArrayList<>();
        void text(String code, String v, int max) { out.add(new String[]{code, "\"" + clean(v, max) + "\""}); }
        void num(String code, String v) { out.add(new String[]{code, v}); }
        /** Pairs that repeat (one set for each month) keep the order they are given in, after the sorted ones. */
        private final List<String[]> tail = new ArrayList<>();
        void repeated(String code, String v) { tail.add(new String[]{code, v}); }
        List<String[]> done() {
            out.sort(Comparator.comparingInt(a -> Integer.parseInt(a[0])));
            out.addAll(tail);
            out.add(new String[]{"9999", "9999"});
            return out;
        }
    }
}
