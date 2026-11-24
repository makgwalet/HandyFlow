package za.co.handyflow.platform.accounting.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.accounting.domain.rules.VatReturnRules.*;

class VatReturnRulesTest {

    private static final UUID OUT = UUID.randomUUID(), IN = UUID.randomUUID(), CONTROL = UUID.randomUUID(),
            SALES = UUID.randomUUID(), BANK = UUID.randomUUID(), OTHER_ASSET = UUID.randomUUID(), GHOST = UUID.randomUUID();

    private static final List<Acct> CHART = List.of(
            new Acct(OUT, "2100", "LIABILITY", "VAT"),
            new Acct(CONTROL, "2110", "LIABILITY", "VAT"),
            new Acct(IN, "1300", "ASSET", "VAT"),
            new Acct(BANK, "1000", "ASSET", "BANK"),
            new Acct(OTHER_ASSET, "1500", "ASSET", null),
            new Acct(SALES, "4000", "INCOME", "REVENUE"));

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static Line dr(UUID a, String v) { return new Line(a, d(v), BigDecimal.ZERO); }
    private static Line cr(UUID a, String v) { return new Line(a, BigDecimal.ZERO, d(v)); }
    private static void eq(String expected, BigDecimal actual) {
        assertTrue(d(expected).compareTo(actual) == 0, "expected " + expected + " but was " + actual);
    }

    @Test void outputVatIsCreditsToVatOutput() {
        Result r = compute(CHART, List.of(cr(OUT, "150"), cr(OUT, "30")));
        eq("180", r.outputVat());
        eq("0", r.inputVat());
        eq("180", r.netVatPayable());
    }

    @Test void aCreditNoteReducesOutputVat() {
        Result r = compute(CHART, List.of(cr(OUT, "150"), dr(OUT, "45")));
        eq("105", r.outputVat());
    }

    @Test void inputVatIsDebitsToTheVatAssetLessCredits() {
        Result r = compute(CHART, List.of(dr(IN, "60"), dr(IN, "15"), cr(IN, "5")));
        eq("70", r.inputVat());
        eq("-70", r.netVatPayable());
    }

    @Test void netVatIsOutputLessInput() {
        Result r = compute(CHART, List.of(cr(OUT, "150"), dr(IN, "60")));
        eq("90", r.netVatPayable());
    }

    @Test void aRefundDueIsNegative() {
        Result r = compute(CHART, List.of(cr(OUT, "10"), dr(IN, "60")));
        eq("-50", r.netVatPayable());
    }

    @Test void otherAccountsAreIgnored() {
        Result r = compute(CHART, List.of(dr(BANK, "1000"), cr(SALES, "870"), cr(CONTROL, "99"), dr(OTHER_ASSET, "5"), dr(CONTROL, "7")));
        eq("0", r.outputVat());
        eq("0", r.inputVat());
    }

    @Test void aLineOnAnUnknownAccountIsLeftOut() {
        Result r = compute(CHART, List.of(cr(GHOST, "500"), cr(OUT, "1")));
        eq("1", r.outputVat());
    }

    @Test void nothingPostedGivesZeros() {
        Result r = compute(CHART, new ArrayList<>());
        eq("0", r.outputVat());
        eq("0", r.inputVat());
        eq("0", r.netVatPayable());
    }

    @Test void missingAmountsCountAsZero() {
        Result r = compute(CHART, List.of(new Line(OUT, null, d("12")), new Line(IN, d("3"), null)));
        eq("12", r.outputVat());
        eq("3", r.inputVat());
    }

    @Test void onlyAnAssetWithTheVatSubtypeCountsAsInput() {
        List<Acct> chart = List.of(new Acct(IN, "1300", "LIABILITY", "VAT"), new Acct(OTHER_ASSET, "1301", "ASSET", "OTHER"));
        Result r = compute(chart, List.of(dr(IN, "9"), dr(OTHER_ASSET, "9")));
        eq("0", r.inputVat());
    }

    @Test void onlyCode2100CountsAsOutput() {
        List<Acct> chart = List.of(new Acct(OUT, "2101", "LIABILITY", "VAT"));
        eq("0", compute(chart, List.of(cr(OUT, "9"))).outputVat());
    }

    @Test void aWorkedQuarter() {
        // Invoices (some overdue, one credited) and a point-of-sale day: VAT 15% of 10 000, 4 000 and 2 000 = 2 400;
        // credit note for 1 150 gross (150 VAT); supplier bills with 690 VAT.
        List<Line> lines = List.of(cr(OUT, "1500"), cr(OUT, "600"), cr(OUT, "300"), dr(OUT, "150"), dr(IN, "690"));
        Result r = compute(CHART, lines);
        eq("2250", r.outputVat());
        eq("690", r.inputVat());
        eq("1560", r.netVatPayable());
    }
}
