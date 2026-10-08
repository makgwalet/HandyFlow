package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.dto.QuestionLibraryDtos.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class GroupDefinitionValidatorTest {

    private static final Set<String> OBS = Set.of("WEIGHT", "GLUCOSE");

    private static Map<String, Object> eq(String q, Object v) { return new HashMap<>(Map.of("q", q, "op", "EQ", "value", v)); }

    private static List<Map<String, Object>> opts(String... values) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (String v : values) l.add(Map.of("value", v, "label", v));
        return l;
    }

    private static QuestionView q(String code, String type, List<Map<String, Object>> options, RuleView... rules) {
        return new QuestionView(code, "Label " + code, null, type, options, null, null, true, false, null, List.of(rules));
    }

    private static GroupDefinition def(List<QuestionView> qs, List<RedFlagView> flags) {
        return new GroupDefinition("Name", null, null, null, null, null, true, null, null, qs, flags);
    }

    private static List<String> problems(GroupDefinition d) { return GroupDefinitionValidator.problems("G1", d, OBS); }

    @Test
    @DisplayName("a well-formed definition has no problems")
    void good() {
        var d = def(List.of(q("reason", "SINGLE_SELECT", opts("A", "B")),
                        q("detail", "TEXT", null, new RuleView("SHOW_WHEN", eq("reason", "A"), null, null))),
                List.of(new RedFlagView("RF1", "Flag", "URGENT", eq("reason", "B"), "m")));
        assertThat(problems(d)).isEmpty();
    }

    @Test
    @DisplayName("bad group code, missing name, no questions")
    void groupLevel() {
        assertThat(GroupDefinitionValidator.problems("1bad", def(List.of(q("x", "TEXT", null)), List.of()), OBS)).isNotEmpty();
        assertThat(problems(new GroupDefinition(" ", null, null, null, null, null, true, null, null, List.of(q("x", "TEXT", null)), List.of())))
                .anyMatch(s -> s.contains("name is required"));
        assertThat(problems(def(List.of(), List.of()))).anyMatch(s -> s.contains("at least one question"));
    }

    @Test
    @DisplayName("ages and sex values are checked")
    void audience() {
        var qs = List.of(q("x", "TEXT", null));
        assertThat(problems(new GroupDefinition("N", null, 10, 5, null, null, true, null, null, qs, List.of())))
                .anyMatch(s -> s.contains("Minimum age"));
        assertThat(problems(new GroupDefinition("N", null, null, null, List.of("X"), null, true, null, null, qs, List.of())))
                .anyMatch(s -> s.contains("Unknown sex"));
    }

    @Test
    @DisplayName("duplicate question codes, unknown types and bad options are reported")
    void questions() {
        assertThat(problems(def(List.of(q("x", "TEXT", null), q("x", "TEXT", null)), List.of()))).anyMatch(s -> s.contains("used twice"));
        assertThat(problems(def(List.of(q("x", "NOPE", null)), List.of()))).anyMatch(s -> s.contains("unknown answer type"));
        assertThat(problems(def(List.of(q("x", "SINGLE_SELECT", null)), List.of()))).anyMatch(s -> s.contains("at least one option"));
        assertThat(problems(def(List.of(q("x", "CHECKLIST", opts("A", "A"))), List.of()))).anyMatch(s -> s.contains("used twice"));
    }

    @Test
    @DisplayName("rules: unknown references, self-dependency, warning message, trigger target, malformed expression")
    void rules() {
        assertThat(problems(def(List.of(q("x", "TEXT", null, new RuleView("SHOW_WHEN", eq("ghost", "A"), null, null))), List.of())))
                .anyMatch(s -> s.contains("unknown question 'ghost'"));
        assertThat(problems(def(List.of(q("x", "TEXT", null, new RuleView("SHOW_WHEN", eq("x", "A"), null, null))), List.of())))
                .anyMatch(s -> s.contains("own answer"));
        assertThat(problems(def(List.of(q("x", "TEXT", null, new RuleView("REQUIRED_WHEN", eq("x", "A"), null, null))), List.of())))
                .isEmpty();
        assertThat(problems(def(List.of(q("x", "TEXT", null), q("y", "TEXT", null, new RuleView("WARNING_WHEN", eq("x", "A"), null, null))), List.of())))
                .anyMatch(s -> s.contains("needs its message"));
        assertThat(problems(def(List.of(q("x", "TEXT", null, new RuleView("TRIGGER_GROUP", eq("x", "A"), null, null))), List.of())))
                .anyMatch(s -> s.contains("group to open"));
        assertThat(problems(def(List.of(q("x", "TEXT", null, new RuleView("TRIGGER_GROUP", eq("x", "A"), null, "G1"))), List.of())))
                .anyMatch(s -> s.contains("open itself"));
        assertThat(problems(def(List.of(q("x", "TEXT", null, new RuleView("REQUIRED_WHEN", eq("x", "A"), null, "OTHER"))), List.of())))
                .anyMatch(s -> s.contains("only TRIGGER_GROUP"));
        assertThat(problems(def(List.of(q("x", "TEXT", null, new RuleView("REQUIRED_WHEN", new HashMap<>(Map.of("q", "x", "op", "BOGUS")), null, null))), List.of())))
                .anyMatch(s -> s.contains("Unknown operator"));
    }

    @Test
    @DisplayName("red flags: nested references and severity are checked")
    void redFlags() {
        var qs = List.of(q("x", "TEXT", null));
        var nested = new HashMap<String, Object>(Map.of("all", List.of(eq("x", "A"), eq("zzz", "B"))));
        assertThat(problems(def(qs, List.of(new RedFlagView("F", "L", "INFO", nested, null))))).anyMatch(s -> s.contains("'zzz'"));
        assertThat(problems(def(qs, List.of(new RedFlagView("F", "L", "HIGH", eq("x", "A"), null))))).anyMatch(s -> s.contains("severity"));
    }

    @Test
    @DisplayName("observation links need a known type and a numeric question")
    void observations() {
        var ok = new QuestionView("w", "Weight", null, "MEASUREMENT", null, null, null, true, false, "WEIGHT", List.of());
        var unknown = new QuestionView("w", "Weight", null, "MEASUREMENT", null, null, null, true, false, "PULSEX", List.of());
        var text = new QuestionView("w", "Weight", null, "TEXT", null, null, null, true, false, "WEIGHT", List.of());
        assertThat(problems(def(List.of(ok), List.of()))).isEmpty();
        assertThat(problems(def(List.of(unknown), List.of()))).anyMatch(s -> s.contains("unknown observation"));
        assertThat(problems(def(List.of(text), List.of()))).anyMatch(s -> s.contains("can feed an observation"));
    }

    @Test
    @DisplayName("a normal answer must fit the question's answer type")
    void normalAnswers() {
        var opts = opts("clear", "wheeze");
        var good = new QuestionView("chest", "Chest", null, "SINGLE_SELECT", opts, null, null, true, false, null, List.of(), "clear");
        var bad = new QuestionView("chest", "Chest", null, "SINGLE_SELECT", opts, null, null, true, false, null, List.of(), "crackles");
        var number = new QuestionView("rate", "Rate", null, "NUMBER", null, null, null, true, false, null, List.of(), 16);
        assertThat(problems(def(List.of(good), List.of()))).isEmpty();
        assertThat(problems(def(List.of(bad), List.of()))).anyMatch(s -> s.contains("one of the options"));
        assertThat(problems(def(List.of(number), List.of()))).anyMatch(s -> s.contains("never preset"));
    }

    @Test
    @DisplayName("array literals are quoted and escaped; empty means NULL (all)")
    void arrayLiteral() {
        assertThat(ClinicQuestionAuthoringService.arrayLiteral(null)).isNull();
        assertThat(ClinicQuestionAuthoringService.arrayLiteral(List.of())).isNull();
        assertThat(ClinicQuestionAuthoringService.arrayLiteral(List.of("MALE", "FEMALE"))).isEqualTo("{\"MALE\",\"FEMALE\"}");
        assertThat(ClinicQuestionAuthoringService.arrayLiteral(List.of("a,b", "c\"d"))).isEqualTo("{\"a,b\",\"c\\\"d\"}");
    }
}
