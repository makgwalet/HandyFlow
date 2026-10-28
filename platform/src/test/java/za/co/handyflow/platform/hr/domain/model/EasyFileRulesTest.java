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
        assertTrue(m.stream().anyMatch(x -> x.contains("IT3(a)")));
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
}
