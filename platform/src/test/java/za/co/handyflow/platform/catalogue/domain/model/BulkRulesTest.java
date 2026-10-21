package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BulkRulesTest {
    @Test
    void changesAPriceByAPercentageAndRoundsToTheCent() {
        assertThat(BulkRules.newPrice(new BigDecimal("100.00"), new BigDecimal("5"))).isEqualByComparingTo("105.00");
        assertThat(BulkRules.newPrice(new BigDecimal("89.90"), new BigDecimal("-10"))).isEqualByComparingTo("80.91");
        assertThat(BulkRules.newPrice(new BigDecimal("0.99"), new BigDecimal("2.5"))).isEqualByComparingTo("1.01");
    }

    @Test
    void checksThePercentage() {
        assertThat(BulkRules.checkPercent(new BigDecimal("-90"))).isEqualByComparingTo("-90");
        assertThatThrownBy(() -> BulkRules.checkPercent(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BulkRules.checkPercent(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BulkRules.checkPercent(new BigDecimal("-91"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BulkRules.checkPercent(new BigDecimal("501"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitsHowManyItemsAtOnce() {
        BulkRules.checkCount(1);
        BulkRules.checkCount(BulkRules.MAX_ITEMS);
        assertThatThrownBy(() -> BulkRules.checkCount(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BulkRules.checkCount(BulkRules.MAX_ITEMS + 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
