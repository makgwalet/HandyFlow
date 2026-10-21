package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.domain.model.CompletenessRules.Facts;

class CompletenessRulesTest {
    private static Facts goods(String desc, boolean cat, String sku, String barcode, String brand, String price) {
        return new Facts(CatalogueItemType.PRODUCT, desc, cat, sku, barcode, brand, price == null ? null : new BigDecimal(price));
    }

    @Test
    void aFullyDescribedProductScoresOneHundred() {
        var r = CompletenessRules.evaluate(goods("Bag", true, "C-1", "600", "PPC", "10"));
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.failed().isEmpty()).isTrue();
    }

    @Test
    void anEmptyProductScoresZeroAndListsEveryMissingThing() {
        var r = CompletenessRules.evaluate(goods(null, false, null, null, null, "0"));
        assertThat(r.score()).isEqualTo(0);
        assertThat(r.failed().size()).isEqualTo(6);
    }

    @Test
    void servicesAreNotExpectedToHaveABarcodeOrBrand() {
        var r = CompletenessRules.evaluate(new Facts(CatalogueItemType.SERVICE, "Call out", true, "SVC-1", null, null, new BigDecimal("450")));
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.checks().size()).isEqualTo(4);
    }

    @Test
    void blankTextCountsAsMissing() {
        var r = CompletenessRules.evaluate(goods("  ", true, " ", "", "", "10"));
        assertThat(r.failed().size()).isEqualTo(4);
        assertThat(r.score()).isEqualTo(35);
    }

    @Test
    void theScoreIsTheWeightOfWhatPassed() {
        // missing barcode (15) and brand (10) of 100
        assertThat(CompletenessRules.evaluate(goods("Bag", true, "C-1", null, null, "10")).score()).isEqualTo(75);
        // a service missing only its description: 55 of 75
        assertThat(CompletenessRules.evaluate(new Facts(CatalogueItemType.LABOUR, null, true, "L", null, null, BigDecimal.ONE)).score()).isEqualTo(73);
    }

    @Test
    void aZeroPriceIsNotAPrice() {
        var r = CompletenessRules.evaluate(goods("Bag", true, "C-1", "600", "PPC", "0"));
        assertThat(r.score()).isEqualTo(80);
        assertThat(r.failed().get(0).code()).isEqualTo("price");
    }

    @Test
    void onlyProductsAndConsumablesAreGoods() {
        assertThat(CompletenessRules.isGoods(CatalogueItemType.PRODUCT)).isTrue();
        assertThat(CompletenessRules.isGoods(CatalogueItemType.CONSUMABLE)).isTrue();
        assertThat(CompletenessRules.isGoods(CatalogueItemType.SERVICE)).isFalse();
        assertThat(CompletenessRules.isGoods(CatalogueItemType.PACKAGE)).isFalse();
    }
}
