package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.domain.model.DataQualityRules.*;

class DataQualityRulesTest {
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private static Row row(String name, String sku, String price) {
        return new Row(UUID.randomUUID(), name, sku, "600" + name.hashCode(), "Brand", "A description", true, CatalogueItemType.PRODUCT, CatalogueItemStatus.ACTIVE,
                new BigDecimal(price), new BigDecimal("1.00"), null, false, false, NOW);
    }

    private static List<Kind> kinds(List<Finding> f) { return f.stream().map(Finding::kind).toList(); }

    @Test
    void aCleanItemHasNoFindings() {
        assertThat(DataQualityRules.evaluate(List.of(row("Cement 50kg", "CEM", "89.90")), NOW)).isEmpty();
    }

    @Test
    void findsMissingBasics() {
        var r = new Row(UUID.randomUUID(), "Thing", null, null, null, null, false, CatalogueItemType.PRODUCT, CatalogueItemStatus.ACTIVE, new BigDecimal("5"), null, null, false, false, NOW);
        assertThat(kinds(DataQualityRules.evaluate(List.of(r), NOW))).containsExactly(Kind.MISSING_CATEGORY, Kind.MISSING_SKU, Kind.MISSING_BARCODE, Kind.MISSING_DESCRIPTION);
    }

    @Test
    void servicesDoNotNeedBarcodes() {
        var r = new Row(UUID.randomUUID(), "Call out", "CALL", null, null, "Visit", true, CatalogueItemType.SERVICE, CatalogueItemStatus.ACTIVE, new BigDecimal("500"), null, null, false, false, NOW);
        assertThat(DataQualityRules.evaluate(List.of(r), NOW)).isEmpty();
    }

    @Test
    void findsPricesThatMakeNoSense() {
        var zero = row("Free", "F", "0.00");
        var loss = new Row(UUID.randomUUID(), "Loss", "L", "1", "B", "d", true, CatalogueItemType.PRODUCT, CatalogueItemStatus.ACTIVE, new BigDecimal("8"), new BigDecimal("10"), new BigDecimal("9"), false, false, NOW);
        assertThat(kinds(DataQualityRules.evaluate(List.of(zero), NOW))).contains(Kind.ZERO_PRICE);
        assertThat(kinds(DataQualityRules.evaluate(List.of(loss), NOW))).containsExactly(Kind.BELOW_COST, Kind.BELOW_MIN_PRICE);
        assertThat(Kind.BELOW_COST.needsPriceAccess).isTrue();
        assertThat(Kind.ZERO_PRICE.needsPriceAccess).isFalse();
    }

    @Test
    void aDraftWithNoPriceIsNotAZeroPriceProblemButAnOldDraftIsStale() {
        var draft = new Row(UUID.randomUUID(), "Draft", "D", "1", "B", "d", true, CatalogueItemType.PRODUCT, CatalogueItemStatus.DRAFT, BigDecimal.ZERO, null, null, false, false, NOW.minusSeconds(40L * 86400));
        assertThat(kinds(DataQualityRules.evaluate(List.of(draft), NOW))).containsExactly(Kind.STALE_DRAFT);
        var fresh = new Row(UUID.randomUUID(), "Draft2", "D2", "2", "B", "d", true, CatalogueItemType.PRODUCT, CatalogueItemStatus.DRAFT, BigDecimal.ZERO, null, null, false, false, NOW.minusSeconds(5L * 86400));
        assertThat(DataQualityRules.evaluate(List.of(fresh), NOW)).isEmpty();
    }

    @Test
    void archivedItemsAndParentsOfVariantsAreLeftAlone() {
        var archived = new Row(UUID.randomUUID(), "Old", null, null, null, null, false, CatalogueItemType.PRODUCT, CatalogueItemStatus.ARCHIVED, BigDecimal.ZERO, null, null, false, false, NOW);
        var parent = new Row(UUID.randomUUID(), "Tee", null, null, null, null, false, CatalogueItemType.PRODUCT, CatalogueItemStatus.ACTIVE, BigDecimal.ZERO, null, null, false, true, NOW);
        assertThat(DataQualityRules.evaluate(List.of(archived, parent), NOW)).isEmpty();
    }

    @Test
    void spotsTheSameProductEnteredTwice() {
        var a = row("Cement 50kg", "A", "89.90");
        var b = row("CEMENT 50 KG.", "B", "90.00");
        var f = DataQualityRules.evaluate(List.of(a, b, row("Sand", "S", "10")), NOW).stream().filter(x -> x.kind() == Kind.POSSIBLE_DUPLICATE).toList();
        assertThat(f.size()).isEqualTo(2);
        assertThat(f.get(0).detail()).contains("Looks like");
    }

    @Test
    void differentSizesAreNotDuplicates() {
        var f = DataQualityRules.evaluate(List.of(row("Cement 25kg", "A", "50"), row("Cement 50kg", "B", "90")), NOW);
        assertThat(kinds(f)).isEmpty();
    }

    @Test
    void closeSpellingsAreDuplicatesButDifferentProductsAreNot() {
        assertThat(DataQualityRules.jaroWinkler("hammer claw", "hamer claw") > DataQualityRules.SIMILARITY).isTrue();
        assertThat(DataQualityRules.jaroWinkler("hammer", "screwdriver") < DataQualityRules.SIMILARITY).isTrue();
        assertThat(DataQualityRules.normalise("Crème  Brûlée, 5kg!")).isEqualTo("creme brulee 5kg");
    }

    @Test
    void variantsAreNotComparedWithEachOther() {
        var rows = new ArrayList<Row>();
        for (String c : List.of("Red", "Blue")) rows.add(new Row(UUID.randomUUID(), "Tee - " + c, "T-" + c, "1" + c, "B", "d", true, CatalogueItemType.PRODUCT, CatalogueItemStatus.ACTIVE, new BigDecimal("5"), null, null, true, false, NOW));
        assertThat(DataQualityRules.evaluate(rows, NOW)).isEmpty();
    }
}
