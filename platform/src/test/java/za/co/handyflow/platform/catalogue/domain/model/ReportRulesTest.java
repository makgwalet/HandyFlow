package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class ReportRulesTest {

    @Test
    void thereAreTwelveReportsWithUniqueCodes() {
        assertThat(ReportRules.ALL.size()).isEqualTo(12);
        assertThat(new HashSet<>(ReportRules.ALL.stream().map(ReportRules.Def::code).toList()).size()).isEqualTo(12);
    }

    @Test
    void reportsThatShowCostOrMarginNeedPriceAccess() {
        for (String code : new String[] {"MARGIN", "LOW_MARGIN", "NO_COST", "PRICE_CHANGES", "PACKAGES"}) assertThat(ReportRules.find(code).orElseThrow().needsPrices()).isTrue();
        for (String code : new String[] {"PRICE_LIST", "BY_CATEGORY", "BY_STATUS", "RECENT_CHANGES", "NO_PICTURE", "NO_BARCODE", "NOT_PUBLISHED"}) assertThat(ReportRules.find(code).orElseThrow().needsPrices()).isFalse();
    }

    @Test
    void aCodeIsFoundIgnoringCaseAndUnknownOnesAreNot() {
        assertThat(ReportRules.find(" margin ").isPresent()).isTrue();
        assertThat(ReportRules.find("nope").isPresent()).isFalse();
        assertThat(ReportRules.find(null).isPresent()).isFalse();
    }

    @Test
    void marginIsAShareOfThePrice() {
        assertThat(ReportRules.margin(new BigDecimal("100"), new BigDecimal("60")).toPlainString()).isEqualTo("40.0");
        assertThat(ReportRules.margin(new BigDecimal("80"), new BigDecimal("100")).toPlainString()).isEqualTo("-25.0");
        assertThat(ReportRules.margin(new BigDecimal("89.90"), new BigDecimal("50")).toPlainString()).isEqualTo("44.4");
        assertThat(ReportRules.margin(null, new BigDecimal("1"))).isNull();
        assertThat(ReportRules.margin(new BigDecimal("1"), null)).isNull();
        assertThat(ReportRules.margin(BigDecimal.ZERO, new BigDecimal("1"))).isNull();
    }

    @Test
    void limitsAreChecked() {
        assertThat(ReportRules.lowMarginLimit(null).intValue()).isEqualTo(20);
        assertThat(ReportRules.lowMarginLimit(0).intValue()).isEqualTo(0);
        assertThatThrownBy(() -> ReportRules.lowMarginLimit(100)).hasMessageContaining("between 0 and 99");
        assertThatThrownBy(() -> ReportRules.lowMarginLimit(-1)).hasMessageContaining("between 0 and 99");
        assertThat(ReportRules.days(null, 30)).isEqualTo(30);
        assertThat(ReportRules.days(730, 30)).isEqualTo(730);
        assertThatThrownBy(() -> ReportRules.days(0, 30)).hasMessageContaining("between 1 and 730");
        assertThatThrownBy(() -> ReportRules.days(731, 30)).hasMessageContaining("between 1 and 730");
    }

    @Test
    void moneyIsTwoDecimalsOrEmpty() {
        assertThat(ReportRules.money(new BigDecimal("5"))).isEqualTo("5.00");
        assertThat(ReportRules.money(null)).isEqualTo("");
    }
}
