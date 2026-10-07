package za.co.handyflow.platform.clinic.domain.question;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A question as the engine sees it. options is a list of {value,label}; rules hold expressions in the
 * JSON-like form documented on {@link QuestionRuleEngine}.
 */
public record QuestionDef(
        String code, String label, AnswerType type,
        List<Map<String, Object>> options,
        BigDecimal min, BigDecimal max,
        boolean defaultVisible, boolean defaultRequired,
        List<RuleDef> rules) {

    public QuestionDef {
        options = options == null ? List.of() : List.copyOf(options);
        rules   = rules   == null ? List.of() : List.copyOf(rules);
    }

    public static QuestionDef of(String code, AnswerType type) {
        return new QuestionDef(code, code, type, null, null, null, true, false, null);
    }

    public QuestionDef withRules(RuleDef... r) {
        return new QuestionDef(code, label, type, options, min, max, defaultVisible, defaultRequired, List.of(r));
    }

    public QuestionDef required() {
        return new QuestionDef(code, label, type, options, min, max, defaultVisible, true, rules);
    }

    public QuestionDef hiddenUnlessShown() {
        return new QuestionDef(code, label, type, options, min, max, false, defaultRequired, rules);
    }

    public QuestionDef withOptions(String... values) {
        List<Map<String, Object>> o = new java.util.ArrayList<>();
        for (String v : values) o.add(Map.of("value", v, "label", v));
        return new QuestionDef(code, label, type, o, min, max, defaultVisible, defaultRequired, rules);
    }

    public QuestionDef withRange(long lo, long hi) {
        return new QuestionDef(code, label, type, options, BigDecimal.valueOf(lo), BigDecimal.valueOf(hi),
                defaultVisible, defaultRequired, rules);
    }
}
