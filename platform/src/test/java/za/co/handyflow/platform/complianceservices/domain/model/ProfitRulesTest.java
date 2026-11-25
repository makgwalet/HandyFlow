package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfitRulesTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test
    void rateInForceIsTheLatestThatHasStarted() {
        List<ProfitRules.Rate> rates = List.of(new ProfitRules.Rate(LocalDate.of(2026, 1, 1), d("300")), new ProfitRules.Rate(LocalDate.of(2026, 7, 1), d("350")));
        assertThat(ProfitRules.rateOn(rates, LocalDate.of(2025, 12, 31)) == null).isTrue();
        assertThat(ProfitRules.rateOn(rates, LocalDate.of(2026, 6, 30)).compareTo(d("300")) == 0).isTrue();
        assertThat(ProfitRules.rateOn(rates, LocalDate.of(2026, 7, 1)).compareTo(d("350")) == 0).isTrue();
        assertThat(ProfitRules.rateOn(List.of(), LocalDate.of(2026, 7, 1)) == null).isTrue();
    }

    @Test
    void costIsMinutesTimesRate() {
        assertThat(ProfitRules.costOf(90, d("400")).compareTo(d("600.00")) == 0).isTrue();
        assertThat(ProfitRules.costOf(20, d("300")).compareTo(d("100.00")) == 0).isTrue();
    }

    @Test
    void rateMustBeSensible() {
        assertThatThrownBy(() -> ProfitRules.checkRate(d("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProfitRules.checkRate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProfitRules.checkRate(d("10001"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(ProfitRules.checkRate(d("350.456")).compareTo(d("350.46")) == 0).isTrue();
    }

    @Test
    void revenueFromAnInvoiceWithVat() {
        // R1,000 bill, invoice total R1,150 with VAT, R575 paid: half collected
        ProfitRules.Revenue r = ProfitRules.revenueOf(d("1000"), "PARTIALLY_PAID", d("1150"), d("575"), d("0"));
        assertThat(r.invoiced().compareTo(d("1000.00")) == 0).isTrue();
        assertThat(r.collected().compareTo(d("500.00")) == 0).isTrue();
    }

    @Test
    void creditNoteReducesWhatWasInvoiced() {
        ProfitRules.Revenue r = ProfitRules.revenueOf(d("1000"), "ISSUED", d("1150"), d("0"), d("575"));
        assertThat(r.invoiced().compareTo(d("500.00")) == 0).isTrue();
        assertThat(r.collected().compareTo(d("0.00")) == 0).isTrue();
    }

    @Test
    void paymentIsNeverMoreThanWhatIsOwed() {
        ProfitRules.Revenue r = ProfitRules.revenueOf(d("1000"), "OVERPAID", d("1150"), d("2000"), d("0"));
        assertThat(r.collected().compareTo(d("1000.00")) == 0).isTrue();
    }

    @Test
    void draftsAndCancelledInvoicesAreWorthNothing() {
        assertThat(ProfitRules.revenueOf(d("1000"), "DRAFT", d("1150"), d("0"), d("0")).invoiced().signum() == 0).isTrue();
        assertThat(ProfitRules.revenueOf(d("1000"), "CANCELLED", d("1150"), d("0"), d("0")).invoiced().signum() == 0).isTrue();
        assertThat(ProfitRules.revenueOf(d("1000"), null, null, null, null).invoiced().signum() == 0).isTrue();
        assertThat(ProfitRules.revenueOf(d("1000"), "ISSUED", d("0"), d("0"), d("0")).invoiced().signum() == 0).isTrue();
    }

    @Test
    void marginIsAPercentageOfWhatWasInvoiced() {
        assertThat(ProfitRules.marginPct(d("1000"), d("600")).compareTo(d("40.0")) == 0).isTrue();
        assertThat(ProfitRules.marginPct(d("1000"), d("1200")).compareTo(d("-20.0")) == 0).isTrue();
        assertThat(ProfitRules.marginPct(d("0"), d("600")) == null).isTrue();
    }

    @Test
    void utilisationIsBillableShareOfHours() {
        assertThat(ProfitRules.utilisationPct(300, 400).compareTo(d("75.0")) == 0).isTrue();
        assertThat(ProfitRules.utilisationPct(0, 0) == null).isTrue();
    }

    @Test
    void costRateEntityTidiesAndRefusesBadInput() {
        TenantId t = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
        UUID u = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        ComplianceCostRate r = ComplianceCostRate.of(t, u, d("350.5"), LocalDate.of(2026, 7, 1), u);
        assertThat(r.getHourlyCost().compareTo(d("350.50")) == 0).isTrue();
        assertThatThrownBy(() -> ComplianceCostRate.of(t, u, d("-1"), LocalDate.of(2026, 7, 1), u)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceCostRate.of(t, u, d("100"), null, u)).isInstanceOf(IllegalArgumentException.class);
    }
}
