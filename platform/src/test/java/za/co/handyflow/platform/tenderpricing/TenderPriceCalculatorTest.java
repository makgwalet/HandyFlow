package za.co.handyflow.platform.tenderpricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.tenderpricing.TenderPriceCalculator.Breakdown;
import za.co.handyflow.platform.tenderpricing.TenderPriceCalculator.Line;
import za.co.handyflow.platform.tenderpricing.TenderPriceCalculator.Settings;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The tender price arithmetic (ADR-004), checked against figures worked out by hand. */
class TenderPriceCalculatorTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static Line line(String section, String qty, String cost) { return new Line(section, d(qty), d(cost)); }

    private static Settings settings(String oh, String cont, String profit, boolean vat, String vatRate) {
        return new Settings(d(oh), d(cont), d(profit), vat, d(vatRate));
    }

    @Test
    @DisplayName("worked example: direct 1000, overhead 10% = 100, contingency 5% = 50, profit 8% of 1150 = 92, ex VAT 1242, VAT 15% = 186.30, total 1428.30")
    void workedExample() {
        Breakdown b = TenderPriceCalculator.calculate(
                List.of(line("A", "10", "60.00"), line("A", "5", "80.00")),
                settings("10", "5", "8", true, "15"));

        assertThat(b.directCost()).isEqualByComparingTo("1000.00");
        assertThat(b.overhead()).isEqualByComparingTo("100.00");
        assertThat(b.contingency()).isEqualByComparingTo("50.00");
        assertThat(b.profit()).isEqualByComparingTo("92.00");
        assertThat(b.priceExVat()).isEqualByComparingTo("1242.00");
        assertThat(b.vat()).isEqualByComparingTo("186.30");
        assertThat(b.priceInclVat()).isEqualByComparingTo("1428.30");
    }

    @Test
    @DisplayName("profit is charged on cost plus overhead plus contingency, not on direct cost alone")
    void profitBase() {
        Breakdown b = TenderPriceCalculator.calculate(List.of(line("A", "1", "100")), settings("10", "10", "10", false, "15"));
        // base 120, profit 12 (on direct alone it would be 10)
        assertThat(b.profit()).isEqualByComparingTo("12.00");
    }

    @Test
    @DisplayName("overhead and contingency are both charged on direct cost, not on each other")
    void overheadAndContingencyBase() {
        Breakdown b = TenderPriceCalculator.calculate(List.of(line("A", "1", "200")), settings("10", "10", "0", false, "15"));
        assertThat(b.overhead()).isEqualByComparingTo("20.00");
        assertThat(b.contingency()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("VAT is charged on the price including profit, and is zero when VAT does not apply")
    void vatBaseAndSwitch() {
        List<Line> lines = List.of(line("A", "1", "100"));
        Breakdown on = TenderPriceCalculator.calculate(lines, settings("0", "0", "10", true, "15"));
        assertThat(on.priceExVat()).isEqualByComparingTo("110.00");
        assertThat(on.vat()).isEqualByComparingTo("16.50");
        assertThat(on.priceInclVat()).isEqualByComparingTo("126.50");

        Breakdown off = TenderPriceCalculator.calculate(lines, settings("0", "0", "10", false, "15"));
        assertThat(off.vat()).isEqualByComparingTo("0.00");
        assertThat(off.priceInclVat()).isEqualByComparingTo("110.00");
    }

    @Test
    @DisplayName("the VAT rate used is the one in the settings, not a built-in 15")
    void vatRateComesFromSettings() {
        Breakdown b = TenderPriceCalculator.calculate(List.of(line("A", "1", "100")), settings("0", "0", "0", true, "14"));
        assertThat(b.vat()).isEqualByComparingTo("14.00");
    }

    @Test
    @DisplayName("each line is rounded to cents half-up, and the direct cost is the sum of the ROUNDED lines so the schedule adds up")
    void lineRoundingAddsUp() {
        // 0.333 x 1.00 = 0.333 -> 0.33 ; three of them = 0.99, not 0.999 -> 1.00
        Breakdown b = TenderPriceCalculator.calculate(
                List.of(line("A", "0.333", "1.00"), line("A", "0.333", "1.00"), line("A", "0.333", "1.00")),
                settings("0", "0", "0", false, "15"));
        assertThat(b.directCost()).isEqualByComparingTo("0.99");

        assertThat(TenderPriceCalculator.lineTotal(d("1"), d("0.005"))).isEqualByComparingTo("0.01");
        assertThat(TenderPriceCalculator.lineTotal(d("1"), d("0.004"))).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("every percentage amount is rounded half-up to cents")
    void percentageRounding() {
        // 33.33 x 5% = 1.6665 -> 1.67
        Breakdown b = TenderPriceCalculator.calculate(List.of(line("A", "1", "33.33")), settings("5", "0", "0", false, "15"));
        assertThat(b.overhead()).isEqualByComparingTo("1.67");
        assertThat(b.priceExVat()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("sections keep the order they first appear in, with their line counts and subtotals")
    void sections() {
        Breakdown b = TenderPriceCalculator.calculate(
                List.of(line("Roads", "1", "10"), line("Drainage", "2", "5"), line("Roads", "1", "30")),
                settings("0", "0", "0", false, "15"));

        assertThat(b.sections()).hasSize(2);
        assertThat(b.sections().get(0).section()).isEqualTo("Roads");
        assertThat(b.sections().get(0).lineCount()).isEqualTo(2);
        assertThat(b.sections().get(0).subtotal()).isEqualByComparingTo("40.00");
        assertThat(b.sections().get(1).section()).isEqualTo("Drainage");
        assertThat(b.sections().get(1).subtotal()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("margin is profit as a share of the price ex VAT, to two decimals")
    void margin() {
        Breakdown b = TenderPriceCalculator.calculate(List.of(line("A", "1", "100")), settings("0", "0", "25", false, "15"));
        // profit 25 of 125 = 20.00%  (a 25% markup is a 20% margin)
        assertThat(b.marginPct()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("an empty schedule prices at zero with no margin, rather than dividing by zero")
    void emptySchedule() {
        Breakdown b = TenderPriceCalculator.calculate(List.of(), settings("10", "5", "8", true, "15"));
        assertThat(b.directCost()).isEqualByComparingTo("0.00");
        assertThat(b.priceInclVat()).isEqualByComparingTo("0.00");
        assertThat(b.marginPct()).isNull();
        assertThat(b.sections()).isEmpty();
        assertThat(TenderPriceCalculator.calculate(null, settings("0", "0", "0", true, "15")).priceInclVat()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("zero markups leave the price equal to cost")
    void zeroMarkups() {
        Breakdown b = TenderPriceCalculator.calculate(List.of(line("A", "4", "25")), settings("0", "0", "0", false, "15"));
        assertThat(b.priceExVat()).isEqualByComparingTo("100.00");
        assertThat(b.marginPct()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("missing settings are refused")
    void settingsRequired() {
        assertThatThrownBy(() -> TenderPriceCalculator.calculate(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a missing quantity or cost counts as zero, never as a crash")
    void nullsCountAsZero() {
        assertThat(TenderPriceCalculator.lineTotal(null, d("5"))).isEqualByComparingTo("0.00");
        assertThat(TenderPriceCalculator.lineTotal(d("5"), null)).isEqualByComparingTo("0.00");
    }
}
