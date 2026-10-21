package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariantRulesTest {
    private static void refuses(Runnable r, String msg) { assertThatThrownBy(r::run).isInstanceOf(IllegalArgumentException.class).hasMessage(msg); }

    @Test
    void optionNamesAreCleanedAndBounded() {
        assertThat(VariantRules.optionName(" Size ")).isEqualTo("Size");
        refuses(() -> VariantRules.optionName(" "), "Each option needs a name, for example Size or Colour.");
        refuses(() -> VariantRules.optionName("x".repeat(31)), "An option name can be at most 30 characters.");
    }

    @Test
    void valuesCannotBeEmptyRepeatedOrCarryTheKeySeparators() {
        assertThat(VariantRules.values("Size", List.of(" S ", "M", "L"))).containsExactly("S", "M", "L");
        refuses(() -> VariantRules.values("Size", List.of()), "The option 'Size' needs at least one value.");
        refuses(() -> VariantRules.values("Size", List.of("M", "m")), "The value 'm' appears twice in 'Size'.");
        refuses(() -> VariantRules.values("Size", List.of("a|b")), "A value cannot contain | or =.");
        refuses(() -> VariantRules.values("Size", List.of(" ")), "A value cannot be empty.");
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add("v" + i);
        refuses(() -> VariantRules.values("Size", many), "The option 'Size' can have at most 20 values.");
    }

    @Test
    void anItemHasOneToThreeDistinctOptions() {
        assertThat(VariantRules.optionNames(List.of("Size", "Colour"))).containsExactly("Size", "Colour");
        refuses(() -> VariantRules.optionNames(List.of()), "Add at least one option, for example Size.");
        refuses(() -> VariantRules.optionNames(List.of("a", "b", "c", "d")), "An item can have at most 3 options.");
        refuses(() -> VariantRules.optionNames(List.of("Size", "size")), "The option 'size' appears twice.");
    }

    @Test
    void theMatrixHasOneRowPerCombinationInOptionOrder() {
        var m = VariantRules.combinations(List.of(List.of("S", "M"), List.of("Red", "Blue", "Green")));
        assertThat(m.size()).isEqualTo(6);
        assertThat(m.get(0)).containsExactly("S", "Red");
        assertThat(m.get(2)).containsExactly("S", "Green");
        assertThat(m.get(3)).containsExactly("M", "Red");
        assertThat(VariantRules.combinations(List.of(List.of("Only"))).size()).isEqualTo(1);
    }

    @Test
    void aMatrixOverOneHundredIsRefused() {
        List<String> ten = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThat(VariantRules.combinations(List.of(ten, ten)).size()).isEqualTo(100);
        refuses(() -> VariantRules.combinations(List.of(ten, ten, List.of("a", "b"))), "That makes more than 100 variants. Use fewer values.");
    }

    @Test
    void theKeyIgnoresCaseAndSpacesAroundValues() {
        assertThat(VariantRules.key(List.of("Size", "Colour"), List.of(" Large ", "RED"))).isEqualTo("size=large|colour=red");
        assertThat(VariantRules.key(List.of("Size"), List.of("large"))).isEqualTo(VariantRules.key(List.of("size"), List.of("LARGE")));
        refuses(() -> VariantRules.key(List.of("Size", "Colour"), List.of("L")), "Choose one value for each option.");
    }

    @Test
    void nameAndLabelAreReadable() {
        assertThat(VariantRules.label(List.of("Large", "Red"))).isEqualTo("Large / Red");
        assertThat(VariantRules.variantName("Cement 50kg", List.of("Large", "Red"))).isEqualTo("Cement 50kg — Large / Red");
        refuses(() -> VariantRules.variantName("x".repeat(250), List.of("Large", "Red")), "The variant name would be longer than 255 characters. Shorten the item name or the values.");
    }

    @Test
    void theSuggestedSkuUsesThreeLettersOfEachValue() {
        assertThat(VariantRules.suggestSku("cem-50", List.of("Large", "Red"))).isEqualTo("CEM-50-LAR-RED");
        assertThat(VariantRules.suggestSku("T1", List.of("XL"))).isEqualTo("T1-XL");
        assertThat(VariantRules.suggestSku("T1", List.of("42 mm"))).isEqualTo("T1-42M");
        assertThat(VariantRules.suggestSku("T1", List.of("***"))).isEqualTo("T1-X");
        assertThat(VariantRules.suggestSku(null, List.of("S"))).isNull();
        assertThat(VariantRules.suggestSku(" ", List.of("S"))).isNull();
        assertThat(VariantRules.suggestSku("A".repeat(49), List.of("Large")).length()).isEqualTo(50);
    }
}
