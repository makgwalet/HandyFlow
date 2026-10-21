package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.domain.model.PricingRules.SelfApprovalException;

class PricingRulesTest {
    private static void refuses(Runnable r, String msg) { assertThatThrownBy(r::run).isInstanceOf(IllegalArgumentException.class).hasMessage(msg); }
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void listCodeIsUpperCaseAndStartsWithALetter() {
        assertThat(PricingRules.listCode(" wholesale_2 ")).isEqualTo("WHOLESALE_2");
        refuses(() -> PricingRules.listCode(""), "The price list code is required.");
        refuses(() -> PricingRules.listCode("2fast"), "The price list code must start with a letter and use only letters, numbers and underscores.");
        refuses(() -> PricingRules.listCode("a b"), "The price list code must start with a letter and use only letters, numbers and underscores.");
        refuses(() -> PricingRules.listCode("A".repeat(31)), "The price list code can be at most 30 characters.");
    }

    @Test
    void listNameIsRequiredAndBounded() {
        assertThat(PricingRules.listName(" Trade ")).isEqualTo("Trade");
        refuses(() -> PricingRules.listName(" "), "The price list name is required.");
        refuses(() -> PricingRules.listName("x".repeat(61)), "The price list name can be at most 60 characters.");
    }

    @Test
    void noDateMeansTodayButThePastAndTheFarFutureAreRefused() {
        assertThat(PricingRules.effectiveDate(null, TODAY)).isEqualTo(TODAY);
        assertThat(PricingRules.effectiveDate(TODAY, TODAY)).isEqualTo(TODAY);
        assertThat(PricingRules.effectiveDate(TODAY.plusDays(30), TODAY)).isEqualTo(TODAY.plusDays(30));
        refuses(() -> PricingRules.effectiveDate(TODAY.minusDays(1), TODAY), "The effective date cannot be in the past.");
        refuses(() -> PricingRules.effectiveDate(TODAY.plusDays(731), TODAY), "The effective date can be at most two years ahead.");
        assertThat(PricingRules.effectiveDate(TODAY.plusDays(730), TODAY)).isEqualTo(TODAY.plusDays(730));
    }

    @Test
    void aReasonIsRequiredOnlyWhenAnApproverMustReadIt() {
        assertThat(PricingRules.reason("  ", false)).isNull();
        assertThat(PricingRules.reason(" cost up ", true)).isEqualTo("cost up");
        refuses(() -> PricingRules.reason(" ", true), "Say why the price is changing (at least 3 characters).");
        refuses(() -> PricingRules.reason("x".repeat(256), false), "The reason can be at most 255 characters.");
    }

    @Test
    void aRejectionNeedsANote() {
        refuses(() -> PricingRules.note("", true), "Say why (at least 3 characters).");
        assertThat(PricingRules.note(" ok ", false)).isEqualTo("ok");
        assertThat(PricingRules.note("", false)).isNull();
    }

    @Test
    void aPriceBelowTheMinimumIsRefused() {
        PricingRules.checkAboveMinimum(d("100"), d("100"));
        PricingRules.checkAboveMinimum(d("100"), null);
        PricingRules.checkAboveMinimum(null, d("100"));
        refuses(() -> PricingRules.checkAboveMinimum(d("99.99"), d("100")), "The price cannot be below the minimum price of R 100.00.");
    }

    @Test
    void theProposerCannotDecideTheirOwnChange() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PricingRules.checkMakerChecker(a, b);
        PricingRules.checkMakerChecker(null, b);
        assertThatThrownBy(() -> PricingRules.checkMakerChecker(a, a)).isInstanceOf(SelfApprovalException.class)
                .hasMessage("You proposed this change, so someone else has to decide it.");
        assertThatThrownBy(() -> PricingRules.checkMakerChecker(a, null)).isInstanceOf(SelfApprovalException.class);
    }

    @Test
    void marginAndChangePercentAreRoundedToOneDecimal() {
        assertThat(PricingRules.marginPercent(d("850"), d("680"))).isEqualTo(d("20.0"));
        assertThat(PricingRules.marginPercent(d("120"), d("80"))).isEqualTo(d("33.3"));
        assertThat(PricingRules.marginPercent(d("0"), d("80"))).isNull();
        assertThat(PricingRules.marginPercent(d("10"), null)).isNull();
        assertThat(PricingRules.changePercent(d("100"), d("120"))).isEqualTo(d("20.0"));
        assertThat(PricingRules.changePercent(d("120"), d("100"))).isEqualTo(d("-16.7"));
        assertThat(PricingRules.changePercent(null, d("100"))).isNull();
        assertThat(PricingRules.changePercent(d("0"), d("100"))).isNull();
    }

    @Test
    void vatIsAddedUnlessTheItemIsExempt() {
        assertThat(PricingRules.withVat(d("850"), d("15"), false)).isEqualTo(d("977.50"));
        assertThat(PricingRules.withVat(d("850"), d("15"), true)).isEqualTo(d("850.00"));
        assertThat(PricingRules.withVat(d("99.99"), d("15"), false)).isEqualTo(d("114.99"));
        assertThat(PricingRules.withVat(null, d("15"), false)).isNull();
    }

    @Test
    void theAuditLineShowsOldNewAndPercent() {
        assertThat(PricingRules.describe("Retail", d("100"), d("120"), TODAY)).isEqualTo("Retail: 100.00 -> 120.00 (+20.0%), from 2026-10-21");
        assertThat(PricingRules.describe("Trade", null, d("90"), TODAY)).isEqualTo("Trade: (none) -> 90.00, from 2026-10-21");
        assertThat(PricingRules.describe("Retail", d("120"), d("100"), TODAY)).isEqualTo("Retail: 120.00 -> 100.00 (-16.7%), from 2026-10-21");
    }

    @Test
    void statusKnowsWhichChangesAreStillOpen() {
        assertThat(PriceChangeStatus.PENDING.open()).isTrue();
        assertThat(PriceChangeStatus.SCHEDULED.open()).isTrue();
        assertThat(PriceChangeStatus.APPLIED.open()).isFalse();
        assertThat(PriceChangeStatus.REJECTED.open()).isFalse();
    }
}
