package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.EasyFileRules.Address;
import za.co.handyflow.platform.hr.domain.model.EasyFileRules.Employer;
import za.co.handyflow.platform.hr.domain.model.EasyFileRules.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EasyFileRulesTest {

    private static final Address ADDR = new Address("12 Mining Road", "Germiston", "Johannesburg", "1401");
    private final Employer employer = new Employer("Acme Mining (Pty) Ltd", "7123456789", "L123456789", "U123456789", "Thandi", "Mokoena",
            "011 555 0100", "payroll@acme.test", "41000", ADDR, 2027, "ANNUAL", false);

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private Person person(String idNumber, String type, List<TaxCertificateRules.Line> lines) {
        return new Person("712345678920270200000000000001", type, "Dlamini", "Jane Mary Ann", idNumber, "1234567890", LocalDate.of(1990, 5, 17), "EMP-1",
                "jane@acme.test", "082 555 0101", new Address("5 Oak Street", "Bedfordview", "Johannesburg", "2007"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), d("12"), d("12"), lines);
    }

    private static final List<TaxCertificateRules.Line> LINES = List.of(
            new TaxCertificateRules.Line("3601", "Income", d("360000.99")),
            new TaxCertificateRules.Line("3605", "Bonus", d("20000.50")),
            new TaxCertificateRules.Line("3810", "Medical benefit", d("5000.00")),
            new TaxCertificateRules.Line("4474", "Employer medical", d("5000.00")),
            new TaxCertificateRules.Line("4102", "PAYE", d("90123.45")),
            new TaxCertificateRules.Line("4116", "Medical credit", d("4368.00")),
            new TaxCertificateRules.Line("4141", "UIF", d("354.24")),
            new TaxCertificateRules.Line("4142", "SDL", d("3809.99")));

    private Person ok() { return person("9005175800083", "IRP5", LINES); }

    @Test
    void aCompleteSetOfDetailsHasNoProblems() {
        assertTrue(EasyFileRules.problems(employer, List.of(ok())).isEmpty(), EasyFileRules.problems(employer, List.of(ok())).toString());
    }

    @Test
    void theFileHasAnEmployerRecordACertificateRecordAndATotalsRecord() {
        String[] rows = EasyFileRules.file(employer, List.of(ok())).split("\r\n");
        assertEquals(3, rows.length);
        assertTrue(rows[0].startsWith("2010,\"Acme Mining (Pty) Ltd\",2015,\"TEST\",2020,7123456789,"));
        assertTrue(rows[1].startsWith("3010,\"712345678920270200000000000001\",3015,\"IRP5\",3020,\"A\",3025,2027,"));
        for (int i = 0; i < 3; i++) assertTrue(rows[i].endsWith(",9999"), rows[i]);
        assertTrue(rows[2].startsWith("6010,2,6020,"));
    }

    @Test
    void aLiveFileSaysLive() {
        Employer live = new Employer(employer.tradingName(), employer.payeRef(), employer.sdlRef(), employer.uifRef(), employer.contactFirstName(),
                employer.contactSurname(), employer.contactPhone(), employer.contactEmail(), employer.sic7(), ADDR, 2027, "ANNUAL", true);
        assertTrue(EasyFileRules.file(live, List.of(ok())).contains("2015,\"LIVE\""));
    }

    @Test
    void randAmountsAreWholeRandRoundedDownAndTaxKeepsItsCents() {
        String row = EasyFileRules.file(employer, List.of(ok())).split("\r\n")[1];
        assertTrue(row.contains(",3601,360000,"), row);
        assertTrue(row.contains(",3605,20000,"), row);
        assertTrue(row.contains(",4102,90123.45,"), row);
        assertTrue(row.contains(",4141,354.24,"), row);
        assertTrue(row.contains(",4142,3809.99,"), row);
        assertTrue(row.contains(",4116,4368.00,"), row);
    }

    @Test
    void theIncomeTotalAndTheTaxTotalAreWorkedOut() {
        String row = EasyFileRules.file(employer, List.of(ok())).split("\r\n")[1];
        assertTrue(row.contains(",3699,385000,"), row);                    // 360000 + 20000 + 5000
        assertTrue(row.contains(",4149,94287.68,"), row);                  // 90123.45 + 354.24 + 3809.99
    }

    @Test
    void anEmployerMedicalBenefitBringsInTheEmployeeCodeAtNil() {
        String row = EasyFileRules.file(employer, List.of(ok())).split("\r\n")[1];
        assertTrue(row.contains(",4005,0,"), row);
    }

    @Test
    void codesComeInAscendingOrder() {
        String row = EasyFileRules.file(employer, List.of(ok())).split("\r\n")[1];
        String[] parts = row.split(",");
        int last = 0;
        for (int i = 0; i < parts.length - 1; i += 2) {
            int c = Integer.parseInt(parts[i]);
            assertTrue(c > last, c + " after " + last);
            last = c;
        }
        assertEquals("9999", parts[parts.length - 1]);
    }

    @Test
    void theTotalsRecordAddsUpTheFile() {
        String text = EasyFileRules.file(employer, List.of(ok(), ok()));
        String[] rows = text.split("\r\n");
        long codes = 0;
        BigDecimal amounts = BigDecimal.ZERO;
        for (int r = 0; r < rows.length - 1; r++) {
            String[] p = rows[r].split(",");
            for (int i = 0; i < p.length - 1; i += 2) {
                int c = Integer.parseInt(p[i]);
                codes += c;
                if (c >= 3601 && c <= 4497) amounts = amounts.add(new BigDecimal(p[i + 1]));
            }
            codes += 9999;
        }
        String[] t = rows[rows.length - 1].split(",");
        assertEquals("6010", t[0]);
        assertEquals("3", t[1]);
        assertEquals(String.valueOf(codes), t[3]);
        assertEquals(amounts.setScale(2).toPlainString(), t[5]);
        assertEquals("9999", t[6]);
    }

    @Test
    void textIsCleanedForTheFile() {
        assertEquals("Jose Maria", EasyFileRules.clean("José, María", 90));
        assertEquals("O'Neil", EasyFileRules.clean(" O\"Neil ", 90));
        assertEquals("JMA", EasyFileRules.initials("Jane Mary Ann"));
        assertEquals("Jane Mary", EasyFileRules.firstTwo("Jane Mary Ann"));
        assertEquals("J", EasyFileRules.initials("jane"));
    }

    @Test
    void anIdThatDoesNotMatchTheBirthdayIsReported() {
        Person p = person("9105175800083", "IRP5", LINES);
        assertTrue(EasyFileRules.problems(employer, List.of(p)).stream().anyMatch(m -> m.contains("does not match the date of birth")));
    }

    @Test
    void aMissingIdAddressAndIt3aAreReported() {
        Person noId = new Person("712345678920270200000000000001", "IT3A", "Dlamini", "Jane", null, null, LocalDate.of(1990, 5, 17), "EMP-2", null, null,
                new Address(null, null, null, null), LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), d("12"), d("12"), LINES);
        List<String> m = EasyFileRules.problems(employer, List.of(noId));
        assertTrue(m.stream().noneMatch(x -> x.contains("IT3(a)")));
        assertTrue(m.stream().anyMatch(x -> x.contains("13-digit ID")));
        assertTrue(m.stream().anyMatch(x -> x.contains("residential street address")));
    }

    @Test
    void theEmployerDetailsSarsRequiresAreChecked() {
        Employer bad = new Employer(" ", "123", null, null, null, null, null, null, "4100", new Address(null, null, null, "14"), 2027, "ANNUAL", false);
        List<String> m = EasyFileRules.problems(bad, List.of(ok()));
        assertEquals(7L, m.stream().filter(x -> x.startsWith("Employer")).count(), m.toString());
    }

    @Test
    void thePeriodMonthIsAugustOrFebruary() {
        assertEquals("202608", EasyFileRules.reconciliationMonth(2027, "INTERIM"));
        assertEquals("202702", EasyFileRules.reconciliationMonth(2027, "ANNUAL"));
    }

    @Test
    void noCertificatesIsAProblem() {
        assertTrue(EasyFileRules.problems(employer, new ArrayList<>()).stream().anyMatch(m -> m.contains("no issued certificates")));
    }

    private Person passportHolder(String number, String country) {
        return new Person("712345678920270200000000000001", "IRP5", "Moyo", "Tendai", null, "1234567890", LocalDate.of(1990, 5, 17), "EMP-3",
                "tendai@acme.test", "082 555 0102", new Address("5 Oak Street", "Bedfordview", "Johannesburg", "2007"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), d("12"), d("12"), LINES, number, country);
    }

    @Test
    void aPassportHolderIsWrittenWithThePassportInsteadOfAnId() {
        Person p = passportHolder("ab 123456".replace(" ", ""), "ZW");
        assertTrue(EasyFileRules.problems(employer, List.of(p)).stream().noneMatch(m -> m.contains("ID number") || m.contains("passport")));
        String row = EasyFileRules.file(employer, List.of(p)).split("\r\n")[1];
        assertTrue(row.contains(",3070,\"AB123456\","), row);
        assertTrue(row.contains(",3075,\"ZW\","), row);
        assertFalse(row.contains(",3060,"), row);
    }

    @Test
    void aValidIdWinsOverAPassport() {
        Person p = new Person("712345678920270200000000000001", "IRP5", "Dlamini", "Jane Mary Ann", "9005175800083", "1234567890", LocalDate.of(1990, 5, 17), "EMP-1",
                "jane@acme.test", "082 555 0101", new Address("5 Oak Street", "Bedfordview", "Johannesburg", "2007"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), d("12"), d("12"), LINES, "AB123456", "ZW");
        String row = EasyFileRules.file(employer, List.of(p)).split("\r\n")[1];
        assertTrue(row.contains(",3060,9005175800083,"), row);
        assertFalse(row.contains(",3070,"), row);
    }

    @Test
    void aPassportWithoutItsCountryOrNeitherIsReported() {
        assertTrue(EasyFileRules.problems(employer, List.of(passportHolder("AB123456", null))).stream().anyMatch(m -> m.contains("passport number with its country")));
        assertTrue(EasyFileRules.problems(employer, List.of(passportHolder(null, null))).stream().anyMatch(m -> m.contains("13-digit ID number, or a passport")));
    }

    @Test
    void anIt3aCarriesItsReasonCodeAndNoTax() {
        List<TaxCertificateRules.Line> lines = List.of(new TaxCertificateRules.Line("3601", "Income", d("5000.00")),
                new TaxCertificateRules.Line("4102", "PAYE", d("0.00")), new TaxCertificateRules.Line("4141", "UIF", d("100.00")),
                new TaxCertificateRules.Line("4142", "SDL", d("50.00")));
        Person p = person("9005175800083", "IT3A", lines);
        assertTrue(EasyFileRules.problems(employer, List.of(p)).isEmpty(), EasyFileRules.problems(employer, List.of(p)).toString());
        String row = EasyFileRules.file(employer, List.of(p)).split("\r\n")[1];
        assertTrue(row.contains(",3015,\"IT3(a)\","), row);
        assertTrue(row.contains(",4150,2,"), row);
        List<TaxCertificateRules.Line> withCredit = new ArrayList<>(lines);
        withCredit.add(new TaxCertificateRules.Line("4116", "Medical credit", d("376.00")));
        assertTrue(EasyFileRules.file(employer, List.of(person("9005175800083", "IT3A", withCredit))).split("\r\n")[1].contains(",4150,8,"));
        assertFalse(EasyFileRules.file(employer, List.of(ok())).contains(",4150,"));
    }

    @Test
    void totalsAlreadyOnTheCertificateAreWorkedOutAgainNotWrittenTwice() {
        List<TaxCertificateRules.Line> lines = new ArrayList<>(LINES);
        lines.add(new TaxCertificateRules.Line("3699", "Total", d("999999.99")));
        lines.add(new TaxCertificateRules.Line("4149", "Total", d("1.00")));
        String row = EasyFileRules.file(employer, List.of(person("9005175800083", "IRP5", lines))).split("\r\n")[1];
        assertEquals(1, row.split(",3699,", -1).length - 1);
        assertEquals(1, row.split(",4149,", -1).length - 1);
        assertTrue(row.contains(",3699,385000,"), row);
        assertTrue(row.contains(",4149,94287.68,"), row);
    }

    private Person withEti(List<EtiRules.Month> months) {
        return new Person("712345678920270200000000000001", "IRP5", "Dlamini", "Jane Mary Ann", "9005175800083", "1234567890", LocalDate.of(1990, 5, 17), "EMP-1",
                "jane@acme.test", "082 555 0101", new Address("5 Oak Street", "Bedfordview", "Johannesburg", "2007"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), d("12"), d("12"), LINES, null, null, LocalDate.of(2026, 3, 1), months);
    }

    private static EtiRules.Month month(int m, String eti, int cycle, int hours) {
        return new EtiRules.Month(m, d("4500.00"), d("4838.40"), d(eti), cycle, hours, d("4500.00"));
    }

    @Test
    void theEtiIndicatorStartDateAndMonthlySetsAreWritten() {
        String row = EasyFileRules.file(employer, List.of(withEti(List.of(month(3, "1000.00", 1, 160), month(4, "0", 0, 160))))).split("\r\n")[1];
        assertTrue(row.contains(",3026,\"Y\","), row);
        assertTrue(row.contains(",3190,20260301,"), row);
        int first = row.indexOf(",7002,");
        assertTrue(first > 0);
        String sets = row.substring(first);
        assertTrue(sets.startsWith(",7002,4500.00,7003,4838.40,7004,1000.00,7005,1,7006,03,7007,160,7008,4500.00,7002,4500.00,7003,4838.40,7004,0.00,7005,0,7006,04,7007,160,7008,4500.00,9999"), sets);
        assertTrue(EasyFileRules.problems(employer, List.of(withEti(List.of(month(3, "1000.00", 1, 160))))).isEmpty());
    }

    @Test
    void noEtiMeansTheIndicatorIsNAndNoEtiCodes() {
        String row = EasyFileRules.file(employer, List.of(ok())).split("\r\n")[1];
        assertTrue(row.contains(",3026,\"N\","), row);
        assertFalse(row.contains(",7002,") || row.contains(",3190,"), row);
    }

    @Test
    void etiNeedsHoursAndAStartDate() {
        assertTrue(EasyFileRules.problems(employer, List.of(withEti(List.of(month(3, "1000.00", 1, 0))))).stream().anyMatch(m -> m.contains("no hours")));
        Person noStart = new Person("712345678920270200000000000001", "IRP5", "Dlamini", "Jane Mary Ann", "9005175800083", "1234567890", LocalDate.of(1990, 5, 17), "EMP-1",
                "jane@acme.test", "082 555 0101", new Address("5 Oak Street", "Bedfordview", "Johannesburg", "2007"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), d("12"), d("12"), LINES, null, null, null, List.of(month(3, "1000.00", 1, 160)));
        assertTrue(EasyFileRules.problems(employer, List.of(noStart)).stream().anyMatch(m -> m.contains("started work")));
    }

    @Test
    void theReasonCodeIsNotAnAmountInTheTotalsRecord() {
        List<TaxCertificateRules.Line> lines = List.of(new TaxCertificateRules.Line("3601", "Income", d("5000.00")), new TaxCertificateRules.Line("4102", "PAYE", d("0.00")),
                new TaxCertificateRules.Line("4141", "UIF", d("100.00")), new TaxCertificateRules.Line("4142", "SDL", d("50.00")));
        String[] rows = EasyFileRules.file(employer, List.of(person("9005175800083", "IT3A", lines))).split("\r\n");
        String[] t = rows[rows.length - 1].split(",");
        // 5 000 income + 5 000 3699 + 0 + 100 + 50 + 150 (4149) = 10 300
        assertEquals("10300.00", t[5]);
    }
}
