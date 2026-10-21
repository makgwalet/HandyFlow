package za.co.handyflow.platform.crm.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.CustomFieldRules.Def;
import za.co.handyflow.platform.crm.domain.model.CustomFieldType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomFieldRulesTest {

    private final List<Def> defs = List.of(
            new Def("size", "Fleet size", CustomFieldType.NUMBER, true, List.of()),
            new Def("since", "Customer since", CustomFieldType.DATE, false, List.of()),
            new Def("region", "Region", CustomFieldType.CHOICE, false, List.of("North", "South")),
            new Def("vip", "VIP", CustomFieldType.YES_NO, false, List.of()),
            new Def("note", "Note", CustomFieldType.TEXT, false, List.of()));

    @Test
    void definitionsAreChecked() {
        assertThat(CustomFieldRules.definitionProblem("", CustomFieldType.TEXT, null)).isNotNull();
        assertThat(CustomFieldRules.definitionProblem("x".repeat(61), CustomFieldType.TEXT, null)).isNotNull();
        assertThat(CustomFieldRules.definitionProblem("Size", null, null)).isNotNull();
        assertThat(CustomFieldRules.definitionProblem("Region", CustomFieldType.CHOICE, List.of("North", " north ", ""))).isNotNull();
        assertThat(CustomFieldRules.definitionProblem("Region", CustomFieldType.CHOICE, List.of("North", "South"))).isNull();
        assertThat(CustomFieldRules.cleanOptions(Arrays.asList(" A ", "a", null, "B"))).containsExactly("A", "B");
    }

    @Test
    void keysComeFromLabelsAndStayUnique() {
        assertThat(CustomFieldRules.keyFor("Fleet size (trucks)", Set.of())).isEqualTo("fleet_size_trucks");
        assertThat(CustomFieldRules.keyFor("Fleet size", Set.of("fleet_size", "fleet_size_2"))).isEqualTo("fleet_size_3");
        assertThat(CustomFieldRules.keyFor("???", Set.of())).isEqualTo("field");
    }

    @Test
    void validValuesAreCleanedAndOldInactiveValuesKept() {
        var out = CustomFieldRules.clean(defs, Map.of("size", " 12 000.50 ", "since", "2026-02-28", "region", "South", "vip", "TRUE", "note", "  hi  "), Map.of("old", "kept"));
        assertThat(out).containsEntry("size", "12000.5").containsEntry("since", "2026-02-28").containsEntry("region", "South")
                .containsEntry("vip", "yes").containsEntry("note", "hi").containsEntry("old", "kept");
    }

    @Test
    void aBlankValueClearsAField() {
        var out = CustomFieldRules.clean(defs, Map.of("size", "5"), Map.of("size", "1", "note", "gone", "old", "kept"));
        assertThat(out).doesNotContainKey("note").containsEntry("old", "kept").containsEntry("size", "5");
    }

    @Test
    void badValuesAreRefusedWithAReadableMessage() {
        assertThatThrownBy(() -> CustomFieldRules.clean(defs, Map.of(), null)).hasMessage("Fleet size is required");
        assertThatThrownBy(() -> CustomFieldRules.clean(defs, Map.of("size", "abc"), null)).hasMessage("Fleet size must be a number");
        assertThatThrownBy(() -> CustomFieldRules.clean(defs, Map.of("size", "1".repeat(17)), null)).hasMessage("Fleet size is too large");
        assertThatThrownBy(() -> CustomFieldRules.clean(defs, Map.of("size", "1", "since", "2026-02-30"), null)).hasMessage("Customer since must be a date like 2026-11-30");
        assertThatThrownBy(() -> CustomFieldRules.clean(defs, Map.of("size", "1", "region", "east"), null)).hasMessage("Region must be one of: North, South");
        assertThatThrownBy(() -> CustomFieldRules.clean(defs, Map.of("size", "1", "vip", "maybe"), null)).hasMessage("VIP must be yes or no");
        assertThatThrownBy(() -> CustomFieldRules.clean(defs, Map.of("size", "1", "zzz", "1"), null)).hasMessage("Unknown field 'zzz'");
        assertThatThrownBy(() -> CustomFieldRules.clean(defs, Map.of("size", "1", "note", "x".repeat(501)), null)).hasMessageContaining("at most 500");
    }
}
