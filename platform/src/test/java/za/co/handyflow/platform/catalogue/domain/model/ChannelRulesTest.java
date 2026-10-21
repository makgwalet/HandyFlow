package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.domain.model.ChannelRules.Facts;

class ChannelRulesTest {

    private static Facts facts(CatalogueItemStatus status, boolean variant, int activeVariants, String price, String desc, String sku, String barcode, boolean pic) {
        return new Facts(status, variant, activeVariants, price == null ? null : new BigDecimal(price), "Cement", desc, sku, barcode, pic);
    }

    private static final Facts GOOD = facts(CatalogueItemStatus.ACTIVE, false, 0, "89.90", "Grey cement", "CEM-1", "600123", true);

    @Test
    void codesAreTidyLowerCase() {
        assertThat(ChannelRules.code("  Web-Shop ")).isEqualTo("web-shop");
        assertThat(ChannelRules.code("pos1")).isEqualTo("pos1");
        for (String bad : new String[] {null, "", "a", "-x", "has space", "UPPER_CASE", "x".repeat(31), "a/b"}) {
            assertThatThrownBy(() -> ChannelRules.code(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void namesAndKindsAreChecked() {
        assertThat(ChannelRules.name(" Till ")).isEqualTo("Till");
        assertThatThrownBy(() -> ChannelRules.name("  ")).hasMessageContaining("needs a name");
        assertThatThrownBy(() -> ChannelRules.name("x".repeat(81))).hasMessageContaining("80");
        assertThat(ChannelRules.kind("web")).isEqualTo(ChannelKind.WEB);
        assertThatThrownBy(() -> ChannelRules.kind("radio")).hasMessageContaining("kind of channel");
        assertThatThrownBy(() -> ChannelRules.kind(null)).hasMessageContaining("kind of channel");
    }

    @Test
    void aCompleteActiveItemCanGoAnywhere() {
        for (ChannelKind k : ChannelKind.values()) assertThat(ChannelRules.problems(k, GOOD)).isEmpty();
    }

    @Test
    void onlyActiveItemsWithAPriceCanBePublished() {
        assertThat(ChannelRules.problems(ChannelKind.POS, facts(CatalogueItemStatus.DRAFT, false, 0, "10", null, "S", null, false))).containsExactly("Only active items can be published.");
        assertThat(ChannelRules.problems(ChannelKind.POS, facts(CatalogueItemStatus.ARCHIVED, false, 0, "10", null, "S", null, false)).size()).isEqualTo(1);
        assertThat(ChannelRules.problems(ChannelKind.OTHER, facts(CatalogueItemStatus.ACTIVE, false, 0, null, null, null, null, false))).containsExactly("It has no selling price on this channel's price list.");
        assertThat(ChannelRules.problems(ChannelKind.OTHER, facts(CatalogueItemStatus.ACTIVE, false, 0, "0", null, null, null, false)).size()).isEqualTo(1);
    }

    @Test
    void shopsNeedDescriptionPictureAndSku() {
        var p = ChannelRules.problems(ChannelKind.WEB, facts(CatalogueItemStatus.ACTIVE, false, 0, "10", " ", null, null, false));
        assertThat(p.size()).isEqualTo(3);
        assertThat(p).contains("It needs a description.");
        assertThat(p).contains("It needs a picture.");
        assertThat(p).contains("It needs a SKU.");
        assertThat(ChannelRules.problems(ChannelKind.MARKETPLACE, facts(CatalogueItemStatus.ACTIVE, false, 0, "10", "d", "S", null, false))).containsExactly("It needs a picture.");
        assertThat(ChannelRules.problems(ChannelKind.PRINT, facts(CatalogueItemStatus.ACTIVE, false, 0, "10", null, null, null, false))).isEmpty();
    }

    @Test
    void theTillNeedsSomethingToScanOrType() {
        assertThat(ChannelRules.problems(ChannelKind.POS, facts(CatalogueItemStatus.ACTIVE, false, 0, "10", null, null, null, false))).containsExactly("It needs a SKU or a barcode so it can be found at the till.");
        assertThat(ChannelRules.problems(ChannelKind.POS, facts(CatalogueItemStatus.ACTIVE, false, 0, "10", null, null, "600", false))).isEmpty();
    }

    @Test
    void variantsGoWithTheirMainItemAndAMainItemIsJudgedByItsVariants() {
        assertThat(ChannelRules.problems(ChannelKind.WEB, facts(CatalogueItemStatus.ACTIVE, true, 0, "10", "d", "S", null, true))).containsExactly("Publish the main item. Its variants go with it.");
        assertThat(ChannelRules.problems(ChannelKind.POS, facts(CatalogueItemStatus.ACTIVE, false, 3, null, null, null, null, false))).isEmpty();
    }

    @Test
    void feedCursorAndLimitAreChecked() {
        assertThat(ChannelRules.cursor(null)).isEqualTo(0L);
        assertThat(ChannelRules.cursor(42L)).isEqualTo(42L);
        assertThatThrownBy(() -> ChannelRules.cursor(-1L)).hasMessageContaining("negative");
        assertThat(ChannelRules.feedLimit(null)).isEqualTo(200);
        assertThat(ChannelRules.feedLimit(10)).isEqualTo(10);
        assertThat(ChannelRules.feedLimit(5000)).isEqualTo(500);
        assertThatThrownBy(() -> ChannelRules.feedLimit(0)).hasMessageContaining("at least 1");
    }
}
