package za.co.handyflow.platform.pos.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PosTransactionItem's line-item calculation — the discount ->
 * VAT -> line total pipeline every single sale in the POS module depends
 * on.
 * <p>
 * WHY this class, and why now?
 * <p>
 * This session found and fixed a confirmed, live tax-compliance bug in
 * PosService.resolveVatRate() — every item was silently charged the
 * standard 15% rate regardless of its actual configured rate, because a
 * reflection call always failed and always fell back to the default. That
 * fix was about WHICH rate gets passed in here. This class tests what
 * happens to that rate once it arrives — the actual arithmetic, including
 * rounding. The two are complementary, not redundant: a correct rate fed
 * into a buggy calculation would produce a wrong result just as surely as
 * a wrong rate fed into a correct one.
 * <p>
 * Every expected value below was calculated by hand against the exact
 * formula in PosTransactionItem.create() — not derived from running the
 * code and trusting the output, which would just re-encode whatever bug
 * might already be there.
 */
class PosTransactionItemCalculationTest {

    private PosTransactionItem line(String qty, String unitPrice, String vatRate, String discountPct) {
        return PosTransactionItem.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Test Item", "SKU-001",
                new BigDecimal(qty), new BigDecimal(unitPrice),
                vatRate != null ? new BigDecimal(vatRate) : null,
                discountPct != null ? new BigDecimal(discountPct) : null);
    }

    @Test
    void noDiscountStandardVat() {
        // 2 x R100.00, 15% VAT, no discount
        // subtotal = 200.00, discount = 0.00, afterDiscount = 200.00
        // vat = 200.00 * 15% = 30.00, total = 230.00
        PosTransactionItem item = line("2", "100.00", "15", "0");

        assertThat(item.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(item.getVatAmount()).isEqualByComparingTo("30.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("230.00");
    }

    @Test
    void withDiscount_vatAppliesAfterDiscountNotBeforeIt() {
        // 1 x R500.00, 15% discount, 15% VAT
        // subtotal = 500.00, discount = 500.00 * 15% = 75.00
        // afterDiscount = 425.00 (VAT must be calculated on THIS, not the
        // original 500.00 — a common off-by-one-step bug in discount/VAT
        // pipelines)
        // vat = 425.00 * 15% = 63.75, total = 425.00 + 63.75 = 488.75
        PosTransactionItem item = line("1", "500.00", "15", "15");

        assertThat(item.getDiscountAmount()).isEqualByComparingTo("75.00");
        assertThat(item.getVatAmount()).isEqualByComparingTo("63.75");
        assertThat(item.getLineTotal()).isEqualByComparingTo("488.75");
    }

    @Test
    void vatExemptItem_zeroRate_producesZeroVatNotDefaultFifteen() {
        // Directly exercises what resolveVatRate()'s fix was actually
        // for: an item with vatRate explicitly 0 must produce R0.00 VAT,
        // not silently fall back to the 15% default — that fallback is
        // exactly the bug that was live before this session's fix.
        // 3 x R25.00, 0% VAT, no discount
        PosTransactionItem item = line("3", "25.00", "0", "0");

        assertThat(item.getVatAmount()).isEqualByComparingTo("0.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("75.00");
    }

    @Test
    void roundingHalfUp_onAnOddValueThatDoesNotDivideEvenly() {
        // 1 x R33.33, 15% VAT, no discount
        // vat = 33.33 * 15 / 100 = 4.9995 -> HALF_UP rounds to 5.00
        // (the third decimal is a 9, on the boundary that a HALF_DOWN or
        // truncating rounding mode would resolve differently — this
        // specifically pins down the intended HALF_UP behaviour)
        PosTransactionItem item = line("1", "33.33", "15", "0");

        assertThat(item.getVatAmount()).isEqualByComparingTo("5.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("38.33");
    }

    @Test
    void nullVatRate_defaultsToStandardFifteenPercent() {
        // 1 x R100.00, vatRate omitted entirely, no discount
        PosTransactionItem item = line("1", "100.00", null, "0");

        assertThat(item.getVatRate()).isEqualByComparingTo("15");
        assertThat(item.getVatAmount()).isEqualByComparingTo("15.00");
    }

    @Test
    void nullDiscountPct_defaultsToZero_notNull() {
        // A null discountPct must behave identically to an explicit 0 —
        // if this ever regressed to leaving discountPct null instead of
        // defaulting it, the multiply() call two lines into create()
        // would throw a NullPointerException on the very next sale.
        PosTransactionItem item = line("1", "100.00", "15", null);

        assertThat(item.getDiscountPct()).isEqualByComparingTo("0");
        assertThat(item.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("115.00");
    }

    @Test
    void fractionalQuantity_multipliesCorrectlyNotJustWholeUnits() {
        // POS supports fractional quantities (qty is a scale-3 BigDecimal,
        // not an int) — e.g. 2.5kg of a bulk item. Confirms the
        // calculation isn't accidentally assuming whole units anywhere.
        // 2.5 x R40.00 = R100.00 subtotal, 15% VAT = R15.00
        PosTransactionItem item = line("2.5", "40.00", "15", "0");

        assertThat(item.getVatAmount()).isEqualByComparingTo("15.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("115.00");
    }
}