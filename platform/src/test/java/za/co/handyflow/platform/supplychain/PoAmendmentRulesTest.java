package za.co.handyflow.platform.supplychain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.PoAmendmentRules;
import za.co.handyflow.platform.supplychain.application.internal.PoAmendmentRules.Change;
import za.co.handyflow.platform.supplychain.application.internal.PoAmendmentRules.Result;
import za.co.handyflow.platform.supplychain.domain.model.ScPoLine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PoAmendmentRulesTest {

    private static final UUID TENANT = UUID.randomUUID();
    private ScPoLine widget;  // 10 x R100 @15%
    private ScPoLine gadget;  // 5  x R200 @15%

    @BeforeEach
    void setUp() {
        UUID po = UUID.randomUUID();
        widget = ScPoLine.create(TENANT, po, null, "Widget", null, bd("10"), bd("100"), bd("15"));
        gadget = ScPoLine.create(TENANT, po, null, "Gadget", null, bd("5"), bd("200"), bd("15"));
    }

    @Test
    void quantityIncrease_recomputesTotals() {
        Result r = run(List.of(update(widget, "12", null, null)), Set.of(), Set.of());
        assertThat(r.errors()).isEmpty();
        assertThat(r.linesChanged()).isTrue();
        assertThat(r.subtotal()).isEqualByComparingTo("2200.00");   // 1200 + 1000
        assertThat(r.vatAmount()).isEqualByComparingTo("330.00");
        assertThat(r.total()).isEqualByComparingTo("2530.00");
    }

    @Test
    void quantityBelowReceived_isRejected() {
        widget.recordReceived(bd("6"));
        Result r = run(List.of(update(widget, "5", null, null)), Set.of(), Set.of());
        assertThat(r.errors()).anyMatch(e -> e.contains("already received"));
    }

    @Test
    void priceChange_blockedOnInvoicedLine_butQuantityIncreaseAllowed() {
        Set<UUID> invoiced = Set.of(widget.getId());
        assertThat(run(List.of(update(widget, null, "120", null)), invoiced, Set.of()).errors())
                .anyMatch(e -> e.contains("price/VAT cannot change"));
        assertThat(run(List.of(update(widget, "11", null, null)), invoiced, Set.of()).errors()).isEmpty();
    }

    @Test
    void remove_blockedWhenReceivedReceiptedOrInvoiced() {
        widget.recordReceived(bd("1"));
        assertThat(run(List.of(remove(widget)), Set.of(), Set.of()).errors()).anyMatch(e -> e.contains("received"));
        assertThat(run(List.of(remove(gadget)), Set.of(), Set.of(gadget.getId())).errors()).anyMatch(e -> e.contains("received"));
        assertThat(run(List.of(remove(gadget)), Set.of(gadget.getId()), Set.of()).errors()).anyMatch(e -> e.contains("invoice"));
    }

    @Test
    void removeUntouchedLine_reducesTotal() {
        Result r = run(List.of(remove(gadget)), Set.of(), Set.of());
        assertThat(r.errors()).isEmpty();
        assertThat(r.total()).isEqualByComparingTo("1150.00");
        assertThat(r.summary()).contains("1 removed");
    }

    @Test
    void cannotRemoveEveryLine() {
        Result r = run(List.of(remove(widget), remove(gadget)), Set.of(), Set.of());
        assertThat(r.errors()).anyMatch(e -> e.contains("at least one line"));
    }

    @Test
    void addLine_isIncludedInTotals_andNeedsFields() {
        Result ok = run(List.of(new Change("ADD", null, null, "Bolt", null, bd("100"), bd("1.50"), bd("15"))), Set.of(), Set.of());
        assertThat(ok.errors()).isEmpty();
        assertThat(ok.subtotal()).isEqualByComparingTo("2150.00");  // 1000 + 1000 + 150
        Result bad = run(List.of(new Change("ADD", null, null, "", null, bd("1"), bd("1"), bd("15"))), Set.of(), Set.of());
        assertThat(bad.errors()).isNotEmpty();
    }

    @Test
    void sameLineTwice_noOpAndUnknownLine_areErrors() {
        assertThat(run(List.of(update(widget, "11", null, null), update(widget, "12", null, null)), Set.of(), Set.of()).errors())
                .anyMatch(e -> e.contains("only be changed once"));
        assertThat(run(List.of(update(widget, "10", null, null)), Set.of(), Set.of()).errors())
                .anyMatch(e -> e.contains("nothing to change"));
        assertThat(run(List.of(new Change("UPDATE", UUID.randomUUID(), null, null, null, bd("1"), null, null)), Set.of(), Set.of()).errors())
                .anyMatch(e -> e.contains("not on this purchase order"));
    }

    @Test
    void noLineChanges_isNotMaterial() {
        Result r = run(List.of(), Set.of(), Set.of());
        assertThat(r.linesChanged()).isFalse();
        assertThat(r.total()).isEqualByComparingTo("2300.00");
    }

    private Result run(List<Change> changes, Set<UUID> invoiced, Set<UUID> receipted) {
        return PoAmendmentRules.evaluate(List.of(widget, gadget), changes, invoiced, receipted);
    }

    private Change update(ScPoLine l, String qty, String cost, String vat) {
        return new Change("UPDATE", l.getId(), null, null, null,
                qty == null ? null : bd(qty), cost == null ? null : bd(cost), vat == null ? null : bd(vat));
    }

    private Change remove(ScPoLine l) {
        return new Change("REMOVE", l.getId(), null, null, null, null, null, null);
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
