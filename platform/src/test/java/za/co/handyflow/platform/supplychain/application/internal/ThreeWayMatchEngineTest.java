package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.ScmMatchTolerances.Tolerances;
import za.co.handyflow.platform.supplychain.application.internal.ThreeWayMatchEngine.Input;
import za.co.handyflow.platform.supplychain.application.internal.ThreeWayMatchEngine.Outcome;
import za.co.handyflow.platform.supplychain.domain.enums.MatchExceptionType;
import za.co.handyflow.platform.supplychain.domain.enums.MatchStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ThreeWayMatchEngineTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Tolerances TOL = new Tolerances(bd("2"), bd("0"), bd("2"), bd("0.10"), true, false);

    private final ThreeWayMatchEngine engine = new ThreeWayMatchEngine();
    private UUID supplierId;
    private ScPurchaseOrder po;
    private ScPoLine poLine; // 10 x R100, 15% VAT

    @BeforeEach
    void setUp() {
        supplierId = UUID.randomUUID();
        po = ScPurchaseOrder.create(TENANT, "PO-00001", supplierId, "Acme", null, LocalDate.now(),
                null, "ZAR", null, null, null, USER, "Tester");
        po.submit();
        po.approve(USER, "Boss");
        poLine = ScPoLine.create(TENANT, po.getId(), UUID.randomUUID(), "Widget", null, bd("10"), bd("100"), bd("15"));
        po.recalculateTotals(bd("1000"), bd("150"), bd("1150"));
    }

    @Test
    void cleanLineMatch_isMatched() {
        Outcome o = run(invoice(bd("1000"), bd("150"), bd("1150")),
                List.of(line(bd("10"), bd("100"), bd("15"))), bd("10"), bd("0"));
        assertThat(o.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(o.findings()).isEmpty();
    }

    @Test
    void invoicingMoreThanAccepted_isQuantityVariance() {
        Outcome o = run(invoice(bd("1000"), bd("150"), bd("1150")),
                List.of(line(bd("10"), bd("100"), bd("15"))), bd("6"), bd("0"));
        assertThat(o.status()).isEqualTo(MatchStatus.DISPUTE);
        assertThat(types(o)).contains(MatchExceptionType.QUANTITY_VARIANCE);
    }

    @Test
    void quantityAlreadyInvoicedElsewhere_countsAgainstRemaining() {
        Outcome o = run(invoice(bd("500"), bd("75"), bd("575")),
                List.of(line(bd("5"), bd("100"), bd("15"))), bd("10"), bd("6")); // only 4 left
        assertThat(types(o)).contains(MatchExceptionType.QUANTITY_VARIANCE);
    }

    @Test
    void priceAbovePoPrice_isPriceVariance_butBelowIsFine() {
        Outcome over = run(invoice(bd("1100"), bd("165"), bd("1265")),
                List.of(line(bd("10"), bd("110"), bd("15"))), bd("10"), bd("0"));
        assertThat(types(over)).contains(MatchExceptionType.PRICE_VARIANCE);

        Outcome under = run(invoice(bd("900"), bd("135"), bd("1035")),
                List.of(line(bd("10"), bd("90"), bd("15"))), bd("10"), bd("0"));
        assertThat(under.status()).isEqualTo(MatchStatus.MATCHED);
    }

    @Test
    void noAcceptedReceipt_isMissingGrn_heldNotDisputed() {
        Outcome o = run(invoice(bd("1000"), bd("150"), bd("1150")),
                List.of(line(bd("10"), bd("100"), bd("15"))), bd("0"), bd("0"));
        assertThat(o.status()).isEqualTo(MatchStatus.PARTIAL_MATCH);
        assertThat(types(o)).contains(MatchExceptionType.MISSING_GRN);
    }

    @Test
    void vatMismatchBetweenHeaderAndLines_isTaxVariance() {
        Outcome o = run(invoice(bd("1000"), bd("100"), bd("1100")),
                List.of(line(bd("10"), bd("100"), bd("15"))), bd("10"), bd("0"));
        assertThat(types(o)).contains(MatchExceptionType.TAX_VARIANCE);
    }

    @Test
    void differentSupplier_isDispute() {
        ScSupplierInvoice inv = ScSupplierInvoice.create(TENANT, "SINV-1", UUID.randomUUID(), po.getId(), null,
                "REF-1", LocalDate.now(), LocalDate.now().plusDays(30), "ZAR", bd("1000"), bd("150"), bd("1150"), null, USER);
        Outcome o = engine.evaluate(new Input(inv, List.of(), po, List.of(poLine), Map.of(), Map.of(),
                null, null, 0, TOL));
        assertThat(types(o)).containsExactly(MatchExceptionType.SUPPLIER_MISMATCH);
    }

    @Test
    void headerOnly_cannotBillForUndeliveredGoods() {
        // PO is R1150 but only 4 of 10 received (R460 incl. VAT); invoice asks for the full R1150.
        ScSupplierInvoice inv = invoice(bd("1000"), bd("150"), bd("1150"));
        Outcome o = engine.evaluate(new Input(inv, List.of(), po, List.of(poLine),
                Map.of(poLine.getId(), bd("4")), Map.of(), null, null, 0, TOL));
        assertThat(o.status()).isEqualTo(MatchStatus.DISPUTE);
        assertThat(types(o)).contains(MatchExceptionType.AMOUNT_VARIANCE);
    }

    @Test
    void possibleDuplicate_forcesDispute() {
        ScSupplierInvoice inv = invoice(bd("1000"), bd("150"), bd("1150"));
        Outcome o = engine.evaluate(new Input(inv, List.of(line(bd("10"), bd("100"), bd("15"))), po, List.of(poLine),
                Map.of(poLine.getId(), bd("10")), Map.of(), null, null, 1, TOL));
        assertThat(types(o)).contains(MatchExceptionType.POSSIBLE_DUPLICATE);
        assertThat(o.status()).isEqualTo(MatchStatus.DISPUTE);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Outcome run(ScSupplierInvoice inv, List<ScSupplierInvoiceLine> lines, BigDecimal accepted, BigDecimal prior) {
        return engine.evaluate(new Input(inv, lines, po, List.of(poLine),
                Map.of(poLine.getId(), accepted), Map.of(poLine.getId(), prior), null, null, 0, TOL));
    }

    private ScSupplierInvoice invoice(BigDecimal sub, BigDecimal vat, BigDecimal total) {
        return ScSupplierInvoice.create(TENANT, "SINV-00001", supplierId, po.getId(), null, "REF-1",
                LocalDate.now(), LocalDate.now().plusDays(30), "ZAR", sub, vat, total, null, USER);
    }

    private ScSupplierInvoiceLine line(BigDecimal qty, BigDecimal price, BigDecimal vatRate) {
        return ScSupplierInvoiceLine.create(TENANT, UUID.randomUUID(), 1, poLine.getId(), "Widget", qty, price, vatRate);
    }

    private static List<MatchExceptionType> types(Outcome o) {
        return o.findings().stream().map(ThreeWayMatchEngine.Finding::type).toList();
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
