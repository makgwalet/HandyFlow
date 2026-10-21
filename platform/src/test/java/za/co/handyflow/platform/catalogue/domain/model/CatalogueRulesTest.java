package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueRules.Snapshot;

class CatalogueRulesTest {
    private static void refuses(Runnable r, String msg) { assertThatThrownBy(r::run).isInstanceOf(IllegalArgumentException.class).hasMessage(msg); }
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static Snapshot snap(String name, String sku, BigDecimal price, BigDecimal cost, BigDecimal vat, boolean exempt) {
        return new Snapshot(name, null, sku, null, null, "PRODUCT", "each", "Tools", price, cost, vat, exempt);
    }

    @Test
    void skuIsTrimmedUpperCasedAndBlankBecomesNull() {
        assertThat(CatalogueRules.normalizeSku("  ab-12/x ")).isEqualTo("AB-12/X");
        assertThat(CatalogueRules.normalizeSku("   ")).isNull();
        assertThat(CatalogueRules.normalizeSku(null)).isNull();
    }

    @Test
    void skuRejectsSpacesSymbolsAndLength() {
        refuses(() -> CatalogueRules.normalizeSku("AB 12"), "The SKU can only use letters, numbers and . _ / - (no spaces).");
        refuses(() -> CatalogueRules.normalizeSku("-AB"), "The SKU can only use letters, numbers and . _ / - (no spaces).");
        refuses(() -> CatalogueRules.normalizeSku("A#1"), "The SKU can only use letters, numbers and . _ / - (no spaces).");
        refuses(() -> CatalogueRules.normalizeSku("A".repeat(51)), "The SKU can be at most 50 characters.");
        assertThat(CatalogueRules.normalizeSku("A".repeat(50))).hasSize(50);
    }

    @Test
    void barcodeKeepsCaseAndChecksCharacters() {
        assertThat(CatalogueRules.normalizeBarcode(" 6001234567890 ")).isEqualTo("6001234567890");
        assertThat(CatalogueRules.normalizeBarcode("ab-1")).isEqualTo("ab-1");
        assertThat(CatalogueRules.normalizeBarcode("")).isNull();
        refuses(() -> CatalogueRules.normalizeBarcode("600 123"), "The barcode can only use letters, numbers and . _ / - (no spaces).");
        refuses(() -> CatalogueRules.normalizeBarcode("1".repeat(101)), "The barcode can be at most 100 characters.");
    }

    @Test
    void nameAndBrand() {
        assertThat(CatalogueRules.normalizeName("  Cement 50kg ")).isEqualTo("Cement 50kg");
        refuses(() -> CatalogueRules.normalizeName("  "), "Item name is required.");
        refuses(() -> CatalogueRules.normalizeName(null), "Item name is required.");
        refuses(() -> CatalogueRules.normalizeName("x".repeat(256)), "The name can be at most 255 characters.");
        assertThat(CatalogueRules.normalizeBrand(" ")).isNull();
        refuses(() -> CatalogueRules.normalizeBrand("b".repeat(101)), "The brand can be at most 100 characters.");
    }

    @Test
    void moneyIsNotNegativeNotHugeAndHasTwoDecimals() {
        assertThat(CatalogueRules.checkMoney("The price", d("10"))).isEqualByComparingTo("10.00");
        assertThat(CatalogueRules.checkMoney("The price", d("1.500"))).isEqualByComparingTo("1.50");
        assertThat(CatalogueRules.checkMoney("The price", null)).isNull();
        refuses(() -> CatalogueRules.checkMoney("The price", d("-0.01")), "The price cannot be negative.");
        refuses(() -> CatalogueRules.checkMoney("The cost price", d("10000000000000")), "The cost price is too large.");
        refuses(() -> CatalogueRules.checkMoney("The price", d("1.234")), "The price can have at most 2 decimals.");
    }

    @Test
    void vatExemptForcesZeroRate() {
        assertThat(CatalogueRules.effectiveVat(true, d("15.00"))).isEqualByComparingTo("0");
        assertThat(CatalogueRules.effectiveVat(false, d("15.00"))).isEqualByComparingTo("15");
    }

    @Test
    void lifecycleMovesAreLimited() {
        assertThat(CatalogueItemStatus.DRAFT.canMoveTo(CatalogueItemStatus.ACTIVE)).isTrue();
        assertThat(CatalogueItemStatus.DRAFT.canMoveTo(CatalogueItemStatus.DISCONTINUED)).isFalse();
        assertThat(CatalogueItemStatus.ACTIVE.canMoveTo(CatalogueItemStatus.DRAFT)).isFalse();
        assertThat(CatalogueItemStatus.DISCONTINUED.canMoveTo(CatalogueItemStatus.ACTIVE)).isTrue();
        assertThat(CatalogueItemStatus.ARCHIVED.next()).containsExactly(CatalogueItemStatus.DRAFT);
        assertThat(CatalogueItemStatus.ACTIVE.sellable()).isTrue();
        assertThat(CatalogueItemStatus.SUSPENDED.sellable()).isFalse();
    }

    @Test
    void transitionsNeedAReasonWhereTheyShould() {
        assertThat(CatalogueRules.checkTransition(CatalogueItemStatus.DRAFT, CatalogueItemStatus.ACTIVE, null)).isNull();
        assertThat(CatalogueRules.checkTransition(CatalogueItemStatus.ACTIVE, CatalogueItemStatus.SUSPENDED, " out of stock ")).isEqualTo("out of stock");
        refuses(() -> CatalogueRules.checkTransition(CatalogueItemStatus.ACTIVE, CatalogueItemStatus.DISCONTINUED, "  "), "Say why (at least 3 characters).");
        refuses(() -> CatalogueRules.checkTransition(CatalogueItemStatus.ACTIVE, CatalogueItemStatus.DISCONTINUED, "ab"), "Say why (at least 3 characters).");
        refuses(() -> CatalogueRules.checkTransition(CatalogueItemStatus.ACTIVE, CatalogueItemStatus.ACTIVE, null), "The item is already active.");
        refuses(() -> CatalogueRules.checkTransition(CatalogueItemStatus.DRAFT, CatalogueItemStatus.SUSPENDED, "xxx"), "A draft item cannot become suspended.");
        refuses(() -> CatalogueRules.checkTransition(CatalogueItemStatus.ACTIVE, CatalogueItemStatus.ARCHIVED, "x".repeat(256)), "The reason can be at most 255 characters.");
    }

    @Test
    void archivePermissionAppliesToDiscontinueAndArchiveOnly() {
        assertThat(CatalogueItemStatus.DISCONTINUED.needsArchivePermission()).isTrue();
        assertThat(CatalogueItemStatus.ARCHIVED.needsArchivePermission()).isTrue();
        assertThat(CatalogueItemStatus.SUSPENDED.needsArchivePermission()).isFalse();
        assertThat(CatalogueItemStatus.ACTIVE.needsArchivePermission()).isFalse();
    }

    @Test
    void changesAreDescribedAndPriceChangesAreSpotted() {
        var before = snap("Cement", "C-1", d("100.00"), d("60.00"), d("15.00"), false);
        assertThat(CatalogueRules.describeChanges(before, before)).isEmpty();
        assertThat(CatalogueRules.priceChanged(before, before)).isFalse();

        var renamed = snap("Cement 50kg", "C-1", d("100.0"), d("60"), d("15"), false);
        assertThat(CatalogueRules.describeChanges(before, renamed)).isEqualTo("name: Cement -> Cement 50kg");
        assertThat(CatalogueRules.priceChanged(before, renamed)).isFalse();

        var repriced = snap("Cement", "C-1", d("120.00"), null, d("15.00"), true);
        assertThat(CatalogueRules.describeChanges(before, repriced))
                .isEqualTo("price: 100.00 -> 120.00; cost: 60.00 -> (none); VAT exempt: no -> yes");
        assertThat(CatalogueRules.priceChanged(before, repriced)).isTrue();
    }
}
