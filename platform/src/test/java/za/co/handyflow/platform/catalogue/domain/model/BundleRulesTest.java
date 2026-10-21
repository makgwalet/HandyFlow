package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.domain.model.BundleRules.Component;
import za.co.handyflow.platform.catalogue.domain.model.BundleRules.Line;

class BundleRulesTest {
    final UUID bundle = UUID.randomUUID();
    final UUID a = UUID.randomUUID();
    final UUID b = UUID.randomUUID();
    final Map<UUID, Component> found = new HashMap<>();

    BundleRulesTest() {
        found.put(a, new Component(a, CatalogueItemType.PRODUCT, CatalogueItemStatus.ACTIVE, false, false));
        found.put(b, new Component(b, CatalogueItemType.SERVICE, CatalogueItemStatus.ACTIVE, false, false));
    }

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void aGoodListIsAcceptedWithQuantitiesToThreeDecimals() {
        var out = BundleRules.validate(bundle, CatalogueItemType.PACKAGE, List.of(new Line(a, d("2")), new Line(b, d("0.5"))), found);
        assertThat(out.size()).isEqualTo(2);
        assertThat(out.get(0).quantity().toPlainString()).isEqualTo("2.000");
        assertThat(out.get(1).quantity().toPlainString()).isEqualTo("0.500");
    }

    @Test
    void onlyAPackageCanHaveParts() {
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PRODUCT, List.of(new Line(a, d("1"))), found))
                .hasMessageContaining("Package");
    }

    @Test
    void emptyDuplicateSelfAndOversizedListsAreRefused() {
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PACKAGE, List.of(), found)).hasMessageContaining("at least one");
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PACKAGE, List.of(new Line(a, d("1")), new Line(a, d("2"))), found))
                .hasMessageContaining("only once");
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PACKAGE, List.of(new Line(bundle, d("1"))), found))
                .hasMessageContaining("itself");
        var many = new java.util.ArrayList<Line>();
        for (int i = 0; i < 31; i++) many.add(new Line(UUID.randomUUID(), d("1")));
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PACKAGE, many, found)).hasMessageContaining("at most 30");
    }

    @Test
    void partsMustBeRealSellableNonPackageItems() {
        var pkg = UUID.randomUUID();
        var parent = UUID.randomUUID();
        var archived = UUID.randomUUID();
        var gone = UUID.randomUUID();
        found.put(pkg, new Component(pkg, CatalogueItemType.PACKAGE, CatalogueItemStatus.ACTIVE, false, false));
        found.put(parent, new Component(parent, CatalogueItemType.PRODUCT, CatalogueItemStatus.ACTIVE, false, true));
        found.put(archived, new Component(archived, CatalogueItemType.PRODUCT, CatalogueItemStatus.ARCHIVED, false, false));
        found.put(gone, new Component(gone, CatalogueItemType.PRODUCT, CatalogueItemStatus.ACTIVE, true, false));
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PACKAGE, List.of(new Line(pkg, d("1"))), found)).hasMessageContaining("another package");
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PACKAGE, List.of(new Line(parent, d("1"))), found)).hasMessageContaining("variant");
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PACKAGE, List.of(new Line(archived, d("1"))), found)).hasMessageContaining("archived");
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PACKAGE, List.of(new Line(gone, d("1"))), found)).hasMessageContaining("no longer exists");
        assertThatThrownBy(() -> BundleRules.validate(bundle, CatalogueItemType.PACKAGE, List.of(new Line(UUID.randomUUID(), d("1"))), found)).hasMessageContaining("no longer exists");
    }

    @Test
    void quantitiesMustBePositiveAndSmall() {
        assertThatThrownBy(() -> BundleRules.quantity(d("0"))).hasMessageContaining("more than zero");
        assertThatThrownBy(() -> BundleRules.quantity(d("-1"))).hasMessageContaining("more than zero");
        assertThatThrownBy(() -> BundleRules.quantity(null)).hasMessageContaining("more than zero");
        assertThatThrownBy(() -> BundleRules.quantity(d("1.2345"))).hasMessageContaining("3 decimals");
        assertThatThrownBy(() -> BundleRules.quantity(d("10000"))).hasMessageContaining("9999.999");
        assertThat(BundleRules.quantity(d("1.2500")).toPlainString()).isEqualTo("1.250");
    }

    @Test
    void totalAndSavingAreWorkedOutFromThePartsAndHiddenWhenAPriceIsMissing() {
        var total = BundleRules.total(List.of(d("100.00"), d("25.50")), List.of(d("2"), d("1.000")));
        assertThat(total.toPlainString()).isEqualTo("225.50");
        assertThat(BundleRules.saving(d("200.00"), total, 0).toPlainString()).isEqualTo("25.50");
        assertThat(BundleRules.saving(d("250.00"), total, 0).toPlainString()).isEqualTo("-24.50");
        assertThat(BundleRules.saving(null, total, 0)).isNull();
        assertThat(BundleRules.saving(d("200.00"), total, 1)).isNull();
        assertThat(BundleRules.unpriced(java.util.Arrays.asList(d("1"), null, null))).isEqualTo(2);
    }
}
