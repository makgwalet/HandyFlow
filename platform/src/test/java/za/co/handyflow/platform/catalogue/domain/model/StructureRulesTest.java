package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.domain.model.StructureRules.AttrSpec;

class StructureRulesTest {
    private static void refuses(Runnable r, String msg) { assertThatThrownBy(r::run).isInstanceOf(IllegalArgumentException.class).hasMessage(msg); }
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private static AttrSpec spec(String label, AttributeType t, boolean required, List<String> options, BigDecimal min, BigDecimal max, Integer len) {
        return new AttrSpec(UUID.randomUUID(), label.toLowerCase(), label, t, required, options, min, max, len);
    }

    // ── units ─────────────────────────────────────────────────────────────────

    @Test
    void unitCodeIsTrimmedAndKeepsItsCase() {
        assertThat(StructureRules.unitCode("  m² ")).isEqualTo("m²");
        assertThat(StructureRules.unitCode("Each")).isEqualTo("Each");
    }

    @Test
    void unitCodeRejectsBlankLongAndControlCharacters() {
        refuses(() -> StructureRules.unitCode("  "), "The unit code is required.");
        refuses(() -> StructureRules.unitCode("x".repeat(51)), "The unit code can be at most 50 characters.");
        refuses(() -> StructureRules.unitCode("a\nb"), "The unit code cannot contain line breaks or control characters.");
    }

    @Test
    void unitNameFallsBackToTheCodeAndDecimalsAreBounded() {
        assertThat(StructureRules.unitName("  ", "kg")).isEqualTo("kg");
        assertThat(StructureRules.unitName(" Kilogram ", "kg")).isEqualTo("Kilogram");
        assertThat(StructureRules.unitDecimals(null)).isEqualTo(2);
        assertThat(StructureRules.unitDecimals(0)).isEqualTo(0);
        refuses(() -> StructureRules.unitDecimals(5), "Decimals must be between 0 and 4.");
        refuses(() -> StructureRules.unitDecimals(-1), "Decimals must be between 0 and 4.");
    }

    // ── category tree ─────────────────────────────────────────────────────────

    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), e = UUID.randomUUID();
    // a is the root, b under a, c under b; e is a separate root
    private UUID parent(UUID id) { return id.equals(b) ? a : id.equals(c) ? b : null; }

    @Test
    void movingACategoryInsideItsOwnSubtreeIsALoop() {
        assertThat(StructureRules.wouldCycle(a, c, this::parent)).isTrue();
        assertThat(StructureRules.wouldCycle(a, a, this::parent)).isTrue();
        assertThat(StructureRules.wouldCycle(c, e, this::parent)).isFalse();
        assertThat(StructureRules.wouldCycle(c, a, this::parent)).isFalse();
    }

    @Test
    void depthCountsLevelsFromTheRoot() {
        assertThat(StructureRules.depthOf(a, this::parent)).isEqualTo(1);
        assertThat(StructureRules.depthOf(c, this::parent)).isEqualTo(3);
    }

    @Test
    void placementRefusesLoopsAndTooDeepTrees() {
        refuses(() -> StructureRules.checkPlacement(a, c, 1, this::parent), "A category cannot be moved inside itself or one of its own sub-categories.");
        StructureRules.checkPlacement(e, c, 2, this::parent); // 3 + 2 = 5, allowed
        refuses(() -> StructureRules.checkPlacement(e, c, 3, this::parent), "Categories can be at most 5 levels deep.");
        StructureRules.checkPlacement(null, null, 1, this::parent); // a new root is always fine
    }

    @Test
    void pathReadsFromRootToLeaf() {
        Map<UUID, String> names = Map.of(a, "Building", b, "Cement", c, "Bagged");
        assertThat(StructureRules.pathOf(c, this::parent, names::get)).isEqualTo("Building › Cement › Bagged");
        assertThat(StructureRules.pathOf(a, this::parent, names::get)).isEqualTo("Building");
    }

    @Test
    void subtreeAndAncestorsFollowTheParentLinks() {
        Map<UUID, UUID> parents = new HashMap<>();
        parents.put(a, null); parents.put(b, a); parents.put(c, b); parents.put(e, null);
        assertThat(StructureRules.subtree(a, parents).size()).isEqualTo(3);
        assertThat(StructureRules.subtree(c, parents).size()).isEqualTo(1);
        assertThat(StructureRules.subtree(e, parents).contains(a)).isFalse();
        assertThat(StructureRules.selfAndAncestors(c, this::parent)).isEqualTo(List.of(c, b, a));
    }

    // ── definitions ───────────────────────────────────────────────────────────

    @Test
    void attributeCodeIsLowerCaseSlugStartingWithALetter() {
        assertThat(StructureRules.attributeCode(" Pack_Size2 ")).isEqualTo("pack_size2");
        refuses(() -> StructureRules.attributeCode("2colour"), "The attribute code must start with a letter and use only letters, numbers and underscores.");
        refuses(() -> StructureRules.attributeCode("pack size"), "The attribute code must start with a letter and use only letters, numbers and underscores.");
        refuses(() -> StructureRules.attributeCode(""), "The attribute code is required.");
    }

    @Test
    void listNeedsCleanDistinctOptionsAndOtherTypesNone() {
        assertThat(StructureRules.attributeOptions(AttributeType.LIST, List.of(" Red", "Blue ", ""))).isEqualTo(List.of("Red", "Blue"));
        refuses(() -> StructureRules.attributeOptions(AttributeType.LIST, List.of("Red", "red")), "The option 'red' is listed twice.");
        refuses(() -> StructureRules.attributeOptions(AttributeType.LIST, List.of(" ")), "A list attribute needs at least one option.");
        refuses(() -> StructureRules.attributeOptions(AttributeType.TEXT, List.of("x")), "Only a list attribute can have options.");
        assertThat(StructureRules.attributeOptions(AttributeType.TEXT, null)).isEqualTo(List.of());
    }

    @Test
    void limitsMustSuitTheType() {
        StructureRules.checkLimits(AttributeType.NUMBER, d("0"), d("10"), null);
        refuses(() -> StructureRules.checkLimits(AttributeType.NUMBER, d("5"), d("1"), null), "The minimum cannot be above the maximum.");
        refuses(() -> StructureRules.checkLimits(AttributeType.TEXT, d("1"), null, null), "Minimum and maximum apply to number attributes only.");
        refuses(() -> StructureRules.checkLimits(AttributeType.NUMBER, null, null, 5), "A maximum length applies to text attributes only.");
        refuses(() -> StructureRules.checkLimits(AttributeType.TEXT, null, null, 0), "The maximum length must be between 1 and 500.");
    }

    // ── values ────────────────────────────────────────────────────────────────

    @Test
    void emptyValueMeansNotSet() {
        var t = spec("Colour", AttributeType.TEXT, false, List.of(), null, null, null);
        assertThat(StructureRules.normalizeValue(t, "   ")).isNull();
        assertThat(StructureRules.normalizeValue(t, null)).isNull();
    }

    @Test
    void textObeysItsMaximumLength() {
        var t = spec("Code", AttributeType.TEXT, false, List.of(), null, null, 5);
        assertThat(StructureRules.normalizeValue(t, " abc ")).isEqualTo("abc");
        refuses(() -> StructureRules.normalizeValue(t, "abcdef"), "Code: at most 5 characters.");
    }

    @Test
    void numberIsParsedRangeCheckedAndWrittenPlainly() {
        var n = spec("Length", AttributeType.NUMBER, false, List.of(), d("1"), d("10"), null);
        assertThat(StructureRules.normalizeValue(n, "2,50")).isEqualTo("2.5");
        assertThat(StructureRules.normalizeValue(n, "10.000")).isEqualTo("10");
        refuses(() -> StructureRules.normalizeValue(n, "abc"), "Length: 'abc' is not a number.");
        refuses(() -> StructureRules.normalizeValue(n, "0.5"), "Length: must be at least 1.");
        refuses(() -> StructureRules.normalizeValue(n, "11"), "Length: must be at most 10.");
    }

    @Test
    void booleanAcceptsYesNoTrueFalseOnly() {
        var b = spec("Hazardous", AttributeType.BOOLEAN, false, List.of(), null, null, null);
        assertThat(StructureRules.normalizeValue(b, "Yes")).isEqualTo("true");
        assertThat(StructureRules.normalizeValue(b, "FALSE")).isEqualTo("false");
        refuses(() -> StructureRules.normalizeValue(b, "maybe"), "Hazardous: choose yes or no.");
    }

    @Test
    void dateMustBeIso() {
        var dt = spec("Expiry", AttributeType.DATE, false, List.of(), null, null, null);
        assertThat(StructureRules.normalizeValue(dt, "2026-10-21")).isEqualTo("2026-10-21");
        refuses(() -> StructureRules.normalizeValue(dt, "21/10/2026"), "Expiry: use the date format YYYY-MM-DD.");
        refuses(() -> StructureRules.normalizeValue(dt, "2026-02-30"), "Expiry: use the date format YYYY-MM-DD.");
    }

    @Test
    void listValueMustBeAnOptionAndTakesTheOptionsSpelling() {
        var l = spec("Grade", AttributeType.LIST, false, List.of("Standard", "Premium"), null, null, null);
        assertThat(StructureRules.normalizeValue(l, "premium")).isEqualTo("Premium");
        refuses(() -> StructureRules.normalizeValue(l, "Gold"), "Grade: 'Gold' is not one of the allowed options.");
    }

    @Test
    void checkValuesRejectsUnknownAttributesAndDropsEmptyOnes() {
        var colour = spec("Colour", AttributeType.TEXT, false, List.of(), null, null, null);
        var size = spec("Size", AttributeType.NUMBER, false, List.of(), null, null, null);
        Map<UUID, String> in = new HashMap<>();
        in.put(colour.id(), " Red "); in.put(size.id(), "");
        Map<UUID, String> out = StructureRules.checkValues(List.of(colour, size), in);
        assertThat(out.size()).isEqualTo(1);
        assertThat(out.get(colour.id())).isEqualTo("Red");
        Map<UUID, String> stray = Map.of(UUID.randomUUID(), "x");
        refuses(() -> StructureRules.checkValues(List.of(colour), stray), "An attribute was sent that does not apply to this item's category.");
    }

    @Test
    void requiredAttributesMustHaveAValueOnceTheItemIsNotADraft() {
        var colour = spec("Colour", AttributeType.TEXT, true, List.of(), null, null, null);
        var size = spec("Size", AttributeType.NUMBER, true, List.of(), null, null, null);
        var note = spec("Note", AttributeType.TEXT, false, List.of(), null, null, null);
        var all = List.of(colour, size, note);
        assertThat(StructureRules.missingRequired(all, Map.of(colour.id(), "Red")).size()).isEqualTo(1);
        refuses(() -> StructureRules.checkRequired(all, Map.of()), "Fill in the required attributes: Colour, Size.");
        refuses(() -> StructureRules.checkRequired(all, Map.of(colour.id(), "Red")), "Fill in the required attribute: Size.");
        StructureRules.checkRequired(all, Map.of(colour.id(), "Red", size.id(), "2"));
    }
}
