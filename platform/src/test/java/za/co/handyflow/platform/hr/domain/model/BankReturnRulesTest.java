package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.BankReturnRules.Candidate;
import za.co.handyflow.platform.hr.domain.model.BankReturnRules.Match;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading the bank's returned-items file and matching it to the payments of a run. */
class BankReturnRulesTest {

    private static Candidate pay(String account, String amount, String ref, String name, String status) {
        return new Candidate(UUID.randomUUID(), account, new BigDecimal(amount), ref, name, status);
    }

    private static List<Match> run(String file, Candidate... payments) {
        BankReturnRules.Parsed p = BankReturnRules.parse(file);
        assertThat(p.problem()).isNull();
        return BankReturnRules.match(p.rows(), List.of(payments));
    }

    @Test
    void readsHeadingsInAnyOrderAndWithCommaSemicolonOrTab() {
        for (char sep : new char[]{',', ';', '\t'}) {
            String f = String.join(String.valueOf(sep), "Reason", "Amount", "Account Number", "Name") + "\r\n" + String.join(String.valueOf(sep), "Account closed", "3728.68", "1234567890", "T Nkosi");
            BankReturnRules.Parsed p = BankReturnRules.parse(f);
            assertThat(p.rows().size()).isEqualTo(1);
            assertThat(p.rows().get(0).account()).isEqualTo("1234567890");
            assertThat(p.rows().get(0).amount()).isEqualByComparingTo("3728.68");
            assertThat(p.rows().get(0).reason()).isEqualTo("Account closed");
            assertThat(p.rows().get(0).line()).isEqualTo(2);
        }
    }

    @Test
    void aFileWithoutTheNeededHeadingsIsRefusedWithAReason() {
        assertThat(BankReturnRules.parse("1234567890,3728.68\n").problem()).contains("headings");
        assertThat(BankReturnRules.parse("Account,Amount\n").problem()).contains("no rows");
        assertThat(BankReturnRules.parse("  ").problem()).contains("empty");
        assertThat(BankReturnRules.parse(null).problem()).contains("empty");
    }

    @Test
    void headingsMayComeAfterAFewLinesOfBankPreamble() {
        BankReturnRules.Parsed p = BankReturnRules.parse("Unpaid items report\nRun date,2026-10-25\nAccount,Amount,Reason\n1234567890,10.00,No such account\n");
        assertThat(p.problem()).isNull();
        assertThat(p.rows().get(0).line()).isEqualTo(4);
    }

    @Test
    void quotedCellsKeepTheirSeparatorsAndABomIsIgnored() {
        BankReturnRules.Parsed p = BankReturnRules.parse("﻿Account,Amount,Reason\n1234567890,\"1,234.50\",\"Closed, see branch\"\n");
        assertThat(p.rows().get(0).amount()).isEqualByComparingTo("1234.50");
        assertThat(p.rows().get(0).reason()).isEqualTo("Closed, see branch");
    }

    @Test
    void amountsInCommonWritingsAreUnderstood() {
        assertThat(BankReturnRules.amount("R 1 234,56")).isEqualByComparingTo("1234.56");
        assertThat(BankReturnRules.amount("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(BankReturnRules.amount("1,234.56")).isEqualByComparingTo("1234.56");
        assertThat(BankReturnRules.amount("-1234.56")).isEqualByComparingTo("1234.56");
        assertThat(BankReturnRules.amount("(1234.56)")).isEqualByComparingTo("1234.56");
        assertThat(BankReturnRules.amount("abc")).isNull();
        assertThat(BankReturnRules.amount("")).isNull();
    }

    @Test
    void aTotalLineWithNoAccountOrAmountIsSkipped() {
        BankReturnRules.Parsed p = BankReturnRules.parse("Account,Amount,Reason\n1234567890,10.00,x\n,,Total\n");
        assertThat(p.rows().size()).isEqualTo(1);
    }

    @Test
    void matchesByAccountAndAmountIgnoringLeadingZeros() {
        Candidate a = pay("1234567890", "3728.68", "SAL E1", "Thandi", "PENDING");
        Candidate b = pay("62011234568", "3728.68", "SAL E2", "Sipho", "PENDING");
        List<Match> m = run("Account,Amount,Reason\n0001234567890,3728.68,Closed\n", a, b);
        assertThat(m.get(0).outcome()).isEqualTo(BankReturnRules.MATCHED);
        assertThat(m.get(0).paymentId()).isEqualTo(a.id());
    }

    @Test
    void theAmountMustBeTheSameToTheCent() {
        Candidate a = pay("1234567890", "3728.68", "SAL E1", "Thandi", "PAID");
        assertThat(run("Account,Amount\n1234567890,3728.67\n", a).get(0).outcome()).isEqualTo(BankReturnRules.UNMATCHED);
    }

    @Test
    void twoPaymentsToTheSameAccountAndAmountAreToldApartByReferenceThenName() {
        Candidate a = pay("1234567890", "100.00", "SAL E1", "Thandi", "PENDING");
        Candidate b = pay("1234567890", "100.00", "SAL E2", "Sipho", "PENDING");
        List<Match> byRef = run("Account,Amount,Reference\n1234567890,100,SAL E2\n", a, b);
        assertThat(byRef.get(0).paymentId()).isEqualTo(b.id());
        List<Match> byName = run("Account,Amount,Name\n1234567890,100,thandi\n", a, b);
        assertThat(byName.get(0).paymentId()).isEqualTo(a.id());
        assertThat(run("Account,Amount\n1234567890,100\n", a, b).get(0).outcome()).isEqualTo(BankReturnRules.AMBIGUOUS);
    }

    @Test
    void aPaymentIsMatchedOnceAndTheSecondRowIsADuplicate() {
        Candidate a = pay("1234567890", "100.00", "SAL E1", "Thandi", "PENDING");
        List<Match> m = run("Account,Amount\n1234567890,100\n1234567890,100\n", a);
        assertThat(m.get(0).outcome()).isEqualTo(BankReturnRules.MATCHED);
        assertThat(m.get(1).outcome()).isEqualTo(BankReturnRules.DUPLICATE);
    }

    @Test
    void aPaymentAlreadyReturnedIsReportedAsSuch() {
        Candidate a = pay("1234567890", "100.00", "SAL E1", "Thandi", "RETURNED");
        assertThat(run("Account,Amount\n1234567890,100\n", a).get(0).outcome()).isEqualTo(BankReturnRules.ALREADY_RETURNED);
    }

    @Test
    void aSecondIdenticalPaymentCanStillBeFoundWhenOneIsAlreadyReturned() {
        Candidate done = pay("1234567890", "100.00", "SAL E1", "Thandi", "RETURNED");
        Candidate open = pay("1234567890", "100.00", "SAL E1", "Thandi", "PAID");
        List<Match> m = run("Account,Amount\n1234567890,100\n", done, open);
        assertThat(m.get(0).outcome()).isEqualTo(BankReturnRules.MATCHED);
        assertThat(m.get(0).paymentId()).isEqualTo(open.id());
    }

    @Test
    void anUnreadableRowIsUnmatchedAndAMissingReasonGetsADefault() {
        Candidate a = pay("1234567890", "100.00", "SAL E1", "Thandi", "PENDING");
        List<Match> m = run("Account,Amount,Reason\n1234567890,,x\n1234567890,100,\n", a);
        assertThat(m.get(0).outcome()).isEqualTo(BankReturnRules.UNMATCHED);
        assertThat(BankReturnRules.reasonFor(m.get(1).row())).isEqualTo(BankReturnRules.DEFAULT_REASON);
    }

    @Test
    void aLongReasonIsCutToWhatTheRecordHolds() {
        String f = "Account,Amount,Reason\n1234567890,100," + "x".repeat(400) + "\n";
        assertThat(BankReturnRules.parse(f).rows().get(0).reason().length()).isEqualTo(300);
    }
}
