package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.domain.model.EnrichmentRules.Candidate;
import za.co.handyflow.platform.catalogue.domain.model.EnrichmentRules.Suggestion;

class EnrichmentRulesTest {
    static final UUID BUILDING = UUID.randomUUID();
    static final UUID PAINT = UUID.randomUUID();

    private static Candidate item(String name, UUID cat, String catName, String brand, String sku, String price, String cost) {
        return new Candidate(UUID.randomUUID(), name, cat, catName, brand, sku, price == null ? null : new BigDecimal(price), cost == null ? null : new BigDecimal(cost));
    }

    private static Suggestion of(List<Suggestion> list, String kind) { return list.stream().filter(s -> s.kind().equals(kind)).findFirst().orElse(null); }

    private final List<Candidate> catalogue = new ArrayList<>(List.of(
            item("Cement 25kg", BUILDING, "Building materials", "PPC", "CEM-001", "60", "40"),
            item("Cement 50kg", BUILDING, "Building materials", "PPC", "CEM-002", "110", "75"),
            item("Cement Grey 40kg", BUILDING, "Building materials", "PPC", "CEM-003", "90", "60"),
            item("Brick Face", BUILDING, "Building materials", null, "BRK-01", "3", "2"),
            item("Interior Paint 5L", PAINT, "Paint", "Dulux", "PNT-10", "350", "200"),
            item("Exterior Paint 20L", PAINT, "Paint", "Dulux", "PNT-11", "900", "500")));

    @Test
    void wordsKeepTheNameAndDropSizesAndFiller() {
        assertThat(EnrichmentRules.tokens("Cement 50kg")).containsExactly("cement");
        assertThat(EnrichmentRules.tokens("The Café Table 1,5m with Legs")).containsExactly("cafe", "table", "legs");
        assertThat(EnrichmentRules.tokens(null)).isEmpty();
        assertThat(EnrichmentRules.tokens("12 x 4")).isEmpty();
    }

    @Test
    void anItemWithNothingMissingGetsNoSuggestions() {
        var full = item("Cement 10kg", BUILDING, "Building materials", "PPC", "CEM-009", "40", "25");
        assertThat(new EnrichmentRules.Index(catalogue).suggest(full, true)).isEmpty();
    }

    @Test
    void categoryComesFromSimilarlyNamedItems() {
        var s = item("Cement 10kg", null, null, "PPC", "CEM-009", "40", "25");
        var out = of(new EnrichmentRules.Index(catalogue).suggest(s, false), "CATEGORY");
        assertThat(out.value()).isEqualTo(BUILDING.toString());
        assertThat(out.label()).isEqualTo("Building materials");
        assertThat(out.confidence()).isEqualTo(100);
        assertThat(out.applicable()).isTrue();
        assertThat(out.reason()).contains("Cement 25kg");
    }

    @Test
    void noCategoryIsSuggestedWhenNothingIsSimilarOrTheNeighboursDisagree() {
        var odd = item("Garden Hose", null, null, "X", "GRD-1", "10", "5");
        assertThat(of(new EnrichmentRules.Index(catalogue).suggest(odd, false), "CATEGORY")).isNull();
        var mixed = new ArrayList<>(catalogue);
        mixed.add(item("Wall Paint", PAINT, "Paint", null, null, "1", "1"));
        mixed.add(item("Wall Plaster", BUILDING, "Building materials", null, null, "1", "1"));
        var s = item("Wall Cover", null, null, "X", "WAL-1", "1", "1");
        assertThat(of(new EnrichmentRules.Index(mixed).suggest(s, false), "CATEGORY")).isNull();   // 50/50 is not good enough
    }

    @Test
    void aSingleWeakNeighbourIsNotEnough() {
        var list = List.of(item("Red Cement Mix Special", BUILDING, "Building materials", null, null, null, null));
        var s = item("Cement Mix Blue", null, null, "X", "S-1", "1", "1");   // shares two of four words with the only neighbour
        assertThat(of(new EnrichmentRules.Index(list).suggest(s, false), "CATEGORY")).isNull();
    }

    @Test
    void brandIsReadFromTheNameOrFromSimilarItems() {
        var inName = item("PPC Surebuild 50kg", BUILDING, "Building materials", null, "SB-1", "1", "1");
        var b = of(new EnrichmentRules.Index(catalogue).suggest(inName, false), "BRAND");
        assertThat(b.value()).isEqualTo("PPC");
        assertThat(b.reason()).contains("name contains");
        var similar = item("Cement 10kg", BUILDING, "Building materials", null, "CEM-009", "1", "1");
        var c = of(new EnrichmentRules.Index(catalogue).suggest(similar, false), "BRAND");
        assertThat(c.value()).isEqualTo("PPC");
        assertThat(c.confidence()).isEqualTo(100);
    }

    @Test
    void skuFollowsTheTenantsPatternAndIsNeverInUse() {
        var s = item("Cement 10kg", BUILDING, "Building materials", "PPC", null, "1", "1");
        var out = of(new EnrichmentRules.Index(catalogue).suggest(s, false), "SKU");
        assertThat(out.value()).isEqualTo("CEM-004");
        assertThat(out.applicable()).isTrue();
        var gap = new ArrayList<>(catalogue);
        gap.add(item("Cement 5kg", BUILDING, "Building materials", "PPC", "CEM-004", "1", "1"));
        assertThat(of(new EnrichmentRules.Index(gap).suggest(s, false), "SKU").value()).isEqualTo("CEM-005");
    }

    @Test
    void noSkuIsSuggestedWithoutASimilarItemThatHasAPattern() {
        var s = item("Garden Hose", BUILDING, "Building materials", "X", null, "1", "1");
        assertThat(of(new EnrichmentRules.Index(catalogue).suggest(s, false), "SKU")).isNull();
    }

    @Test
    void priceUsesTheCategorysUsualMarginAndIsOnlyAHint() {
        var s = item("Cement 10kg", BUILDING, "Building materials", "PPC", "CEM-009", null, "40");
        var out = of(new EnrichmentRules.Index(catalogue).suggest(s, true), "PRICE");
        // margins in the category: 31.8, 33.3, 33.3, 33.3 -> median 33.3%; 40 / (1 - 0.333) = 59.97
        assertThat(out.value()).isEqualTo("59.97");
        assertThat(out.applicable()).isFalse();
        assertThat(of(new EnrichmentRules.Index(catalogue).suggest(s, false), "PRICE")).isNull();   // hidden from people who may not see prices
    }

    @Test
    void noPriceHintWithTooFewExamples() {
        var s = item("Interior Sealer", PAINT, "Paint", "Dulux", "PNT-12", null, "100");
        assertThat(of(new EnrichmentRules.Index(catalogue).suggest(s, true), "PRICE")).isNull();   // only two paint items with a margin
    }
}
