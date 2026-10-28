package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The BankServ (ACB) layout is checked field by field against the positions in the bank's document (1-based). */
class BankFileRulesTest {

    private static PayrollPaymentRules.Line line(String name, String branch, String account, String amount, String ref) {
        return new PayrollPaymentRules.Line(name, "FNB", branch, account, new BigDecimal(amount), ref);
    }

    /** Characters from..to inclusive, counting from 1. */
    private static String at(String record, int from, int to) { return record.substring(from - 1, to); }

    private static String[] file(List<PayrollPaymentRules.Line> lines) {
        return BankFileRules.bankserv(lines, "250655", "62011234567", LocalDate.of(2026, 10, 25)).split("\r\n");
    }

    @Test
    void everyRecordIs180CharactersAndTheOrderIsHeadersPaymentsContraTrailers() {
        String[] r = file(List.of(line("Thandi Nkosi", "632005", "1234567890", "3728.68", "SAL PR00005 E1"), line("Sipho Dlamini", "250655", "62011234568", "100", "SAL PR00005 E2")));
        assertThat(r.length).isEqualTo(7);
        for (String x : r) assertThat(x.length()).isEqualTo(180);
        assertThat(List.of(at(r[0], 1, 2), at(r[1], 1, 2), at(r[2], 1, 2), at(r[3], 1, 2), at(r[4], 1, 2), at(r[5], 1, 2), at(r[6], 1, 2)))
                .containsExactly("02", "04", "10", "10", "12", "92", "94");
        assertThat(at(r[0], 3, 180).isBlank()).isTrue();
    }

    @Test
    void aPaymentRecordHasEachFieldWhereTheDocumentPutsIt() {
        String t = file(List.of(line("Thandi Nkosi", "632005", "1234567890", "3728.68", "SAL PR00005 E1")))[2];
        assertThat(at(t, 3, 8)).isEqualTo("250655");                  // user branch
        assertThat(at(t, 9, 19)).isEqualTo("62011234567");            // user nominated account
        assertThat(at(t, 20, 23)).isEqualTo("0000");
        assertThat(at(t, 24, 29)).isEqualTo("000001");                // user sequence number
        assertThat(at(t, 30, 35)).isEqualTo("632005");                // recipient branch
        assertThat(at(t, 36, 46)).isEqualTo("01234567890");           // recipient account, zero filled
        assertThat(at(t, 47, 47)).isEqualTo("1");                     // account type
        assertThat(at(t, 48, 58)).isEqualTo("00000372868");           // R3 728.68 in cents, as the document's own example
        assertThat(at(t, 59, 66)).isEqualTo("00000000");
        assertThat(at(t, 67, 70)).isEqualTo("0000");                  // tax code and the zero fillers
        assertThat(at(t, 71, 90)).isEqualTo("SAL PR00005 E1      "); // reference, 20, left aligned
        assertThat(at(t, 101, 115)).isEqualTo("THANDI NKOSI   ");     // name, 15
        assertThat(at(t, 131, 150)).isEqualTo("00000000000000000000"); // non-standard account not used
    }

    @Test
    void sequenceNumbersAscendAndLongAccountsGoInTheNonStandardField() {
        String[] r = file(List.of(line("A", "632005", "1234567890", "1", "R1"), line("B", "632005", "1234567890123456", "2", "R2")));
        assertThat(at(r[2], 24, 29)).isEqualTo("000001");
        assertThat(at(r[3], 24, 29)).isEqualTo("000002");
        assertThat(at(r[3], 36, 46)).isEqualTo("00000000000");               // zero filled when field 18 is used
        assertThat(at(r[3], 131, 150)).isEqualTo("00001234567890123456");
    }

    @Test
    void theContraNamesTheCompanyAccountAndTheActionDate() {
        String c = file(List.of(line("A", "632005", "1234567890", "1", "R1")))[3];
        assertThat(at(c, 1, 2)).isEqualTo("12");
        assertThat(at(c, 3, 8)).isEqualTo("250655");
        assertThat(at(c, 9, 19)).isEqualTo("62011234567");
        assertThat(at(c, 59, 64)).isEqualTo("261025");                       // YYMMDD
        assertThat(at(c, 20, 58).isBlank()).isTrue();
        assertThat(at(c, 65, 180).isBlank()).isTrue();
    }

    @Test
    void shortCompanyAccountsAreZeroFilledOnTheLeft() {
        String t = BankFileRules.bankserv(List.of(line("A", "632005", "1234567890", "1", "R1")), "250655", "12345", LocalDate.of(2026, 10, 25)).split("\r\n")[2];
        assertThat(at(t, 9, 19)).isEqualTo("00000012345");
    }

    @Test
    void namesAndReferencesAreSimplifiedToCapitalsDigitsAndSpaces() {
        assertThat(BankFileRules.plain("Zoë O'Brien-Müller", 15)).isEqualTo("ZOE O BRIEN MUL");
        assertThat(BankFileRules.plain("  a   b ", 20)).isEqualTo("A B");
        assertThat(BankFileRules.plain(null, 5)).isEmpty();
        String t = file(List.of(line("=cmd|'/c calc'!A1 and a very long name", "632005", "1234567890", "1", "Ref, with: punctuation!")))[2];
        assertThat(at(t, 101, 115)).isEqualTo("CMD C CALC A1 A");
        assertThat(at(t, 71, 90)).isEqualTo("REF WITH PUNCTUATION");
    }

    @Test
    void theActionDateIsNeverADayThatHasGone() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        assertThat(BankFileRules.actionDate(LocalDate.of(2026, 10, 25), today)).isEqualTo(today);
        assertThat(BankFileRules.actionDate(LocalDate.of(2026, 10, 30), today)).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(BankFileRules.actionDate(null, today)).isEqualTo(today);
    }

    @Test
    void badInputIsRefusedInsteadOfWrittenWrong() {
        assertThatThrownBy(() -> BankFileRules.bankserv(List.of(), "250655", "62011234567", LocalDate.now())).hasMessageContaining("no payments");
        assertThatThrownBy(() -> BankFileRules.bankserv(List.of(line("A", "632005", "1234567890", "1", "R")), "25065", "62011234567", LocalDate.now())).hasMessageContaining("6 digits");
        assertThatThrownBy(() -> BankFileRules.bankserv(List.of(line("A", "632005", "1234567890", "1", "R")), "250655", "123456789012", LocalDate.now())).hasMessageContaining("1 to 11 digits");
        assertThatThrownBy(() -> BankFileRules.bankserv(List.of(line("A", "63200", "1234567890", "1", "R")), "250655", "62011234567", LocalDate.now())).hasMessageContaining("Bad bank details");
        assertThatThrownBy(() -> BankFileRules.bankserv(List.of(line("A", "632005", "1234567890", "0", "R")), "250655", "62011234567", LocalDate.now())).hasMessageContaining("cannot be written");
    }

    @Test
    void theLayoutNeedsTheCompanyAccountOnlyForBankServ() {
        assertThat(BankFileRules.layoutProblem("GENERIC_CSV", null, null)).isNull();
        assertThat(BankFileRules.layoutProblem("BANKSERV_ACB", "250655", "62011234567")).isNull();
        assertThat(BankFileRules.layoutProblem("BANKSERV_ACB", null, null)).contains("Payroll settings");
        assertThat(BankFileRules.layoutProblem("NOPE", null, null)).contains("Choose a file layout");
    }

    @Test
    void theFilenameFollowsTheLayout() {
        assertThat(BankFileRules.filename("GENERIC_CSV", "PR00005")).isEqualTo("salary-payments-PR00005.csv");
        assertThat(BankFileRules.filename("BANKSERV_ACB", "PR00005")).isEqualTo("salary-payments-PR00005.txt");
    }

    @Test
    void theSecondPersonRule() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertThat(PayrollPaymentRules.confirmerProblem(true, a, List.of(a))).contains("Another person");
        assertThat(PayrollPaymentRules.confirmerProblem(true, b, List.of(a))).isNull();
        assertThat(PayrollPaymentRules.confirmerProblem(false, a, List.of(a))).isNull();
    }

    @Test
    void theAccountTypeOfEachPaymentIsWrittenAsItsBankServCode() {
        String[] r = file(List.of(
                new PayrollPaymentRules.Line("A", "FNB", "632005", "1234567890", new BigDecimal("10"), "R1", "CURRENT"),
                new PayrollPaymentRules.Line("B", "FNB", "632005", "1234567891", new BigDecimal("10"), "R2", "SAVINGS"),
                new PayrollPaymentRules.Line("C", "FNB", "632005", "1234567892", new BigDecimal("10"), "R3", "TRANSMISSION"),
                new PayrollPaymentRules.Line("D", "FNB", "632005", "1234567893", new BigDecimal("10"), "R4", "BOND"),
                new PayrollPaymentRules.Line("E", "FNB", "632005", "1234567894", new BigDecimal("10"), "R5", null)));
        assertThat(List.of(at(r[2], 47, 47), at(r[3], 47, 47), at(r[4], 47, 47), at(r[5], 47, 47), at(r[6], 47, 47))).containsExactly("1", "2", "3", "4", "1");
    }

    @Test
    void absaIsFiveColumnsWithNoHeadingsInTheOrderTheBankMapsThem() {
        String f = BankFileRules.absaBio(List.of(line("Thandi Nkosi", "632005", "1234 5678 90", "3728.68", "SAL PR00005 E1"), line("Sipho O'Neil", "250655", "62011234568", "100", "SAL E2")));
        String[] rows = f.split("\r\n");
        assertThat(rows.length).isEqualTo(2);
        assertThat(rows[0]).isEqualTo("1234567890,THANDI NKOSI,632005,3728.68,SAL PR00005 E1");
        assertThat(rows[1]).isEqualTo("62011234568,SIPHO O NEIL,250655,100.00,SAL E2");
    }

    @Test
    void absaRefusesBadDetailsOrNothingToPay() {
        assertThatThrownBy(() -> BankFileRules.absaBio(List.of(line("A", "63200", "1234567890", "10", "R")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BankFileRules.absaBio(List.of(line("A", "632005", "123", "10", "R")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BankFileRules.absaBio(List.of(line("A", "632005", "1234567890", "0", "R")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BankFileRules.absaBio(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void absaNeedsNoCompanyAccountAndIsAKnownLayout() {
        assertThat(BankFileRules.layoutProblem(BankFileRules.ABSA_BIO, null, null)).isNull();
        assertThat(BankFileRules.LAYOUTS).contains("ABSA_BIO");
        assertThat(BankFileRules.filename(BankFileRules.ABSA_BIO, "PR-00005").endsWith(".csv")).isTrue();
    }
}
