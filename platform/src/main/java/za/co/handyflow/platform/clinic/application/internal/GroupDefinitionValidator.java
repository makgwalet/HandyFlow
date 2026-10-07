package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.domain.question.AnswerType;
import za.co.handyflow.platform.clinic.domain.question.QuestionRuleEngine;
import za.co.handyflow.platform.clinic.dto.QuestionLibraryDtos.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Checks a question group definition before it is stored. Pure: no database, no Spring. Returns every problem
 * found, as readable lines, so an author can fix them in one pass. Structure only: nothing here judges whether the
 * clinical content is right; that is the reviewer's job (DEC-CLINIC-001).
 */
final class GroupDefinitionValidator {

    private GroupDefinitionValidator() {}

    static final Pattern CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,59}$");
    private static final Set<String> RULE_KINDS = Set.of("SHOW_WHEN", "REQUIRED_WHEN", "ENABLE_WHEN", "WARNING_WHEN", "TRIGGER_GROUP");
    private static final Set<String> CHOICE_TYPES = Set.of("SINGLE_SELECT", "MULTI_SELECT", "RADIO_GROUP", "CHECKLIST");
    private static final Set<String> SEX = Set.of("MALE", "FEMALE", "INTERSEX", "UNKNOWN");
    private static final int MAX_QUESTIONS = 200;

    /** @param groupCode the group's own code (null skips the code check); @param observationCodes valid observation types */
    static List<String> problems(String groupCode, GroupDefinition d, Set<String> observationCodes) {
        List<String> out = new ArrayList<>();
        if (groupCode != null && !CODE.matcher(groupCode).matches()) {
            out.add("Group code must start with a letter and use only letters, digits and underscores (up to 60 characters).");
        }
        if (d == null) { out.add("A definition is required."); return out; }
        if (blank(d.name())) out.add("Group name is required.");
        else if (d.name().length() > 150) out.add("Group name is longer than 150 characters.");
        if (d.minAgeMonths() != null && d.minAgeMonths() < 0) out.add("Minimum age cannot be negative.");
        if (d.maxAgeMonths() != null && d.maxAgeMonths() < 0) out.add("Maximum age cannot be negative.");
        if (d.minAgeMonths() != null && d.maxAgeMonths() != null && d.minAgeMonths() > d.maxAgeMonths()) {
            out.add("Minimum age is above maximum age.");
        }
        if (d.sex() != null) for (String s : d.sex()) if (!SEX.contains(s)) out.add("Unknown sex value '" + s + "'.");
        if (d.visitTypes() != null) for (String v : d.visitTypes()) if (blank(v)) out.add("A visit type is blank.");

        List<QuestionView> qs = d.questions() == null ? List.of() : d.questions();
        if (qs.isEmpty()) out.add("A group needs at least one question.");
        if (qs.size() > MAX_QUESTIONS) out.add("A group can have at most " + MAX_QUESTIONS + " questions.");

        Set<String> codes = new LinkedHashSet<>();
        for (QuestionView q : qs) {
            if (q == null || blank(q.code()) || !CODE.matcher(q.code()).matches()) {
                out.add("Question code '" + (q == null ? "" : q.code()) + "' is not valid (letters, digits, underscore; starts with a letter).");
            } else if (!codes.add(q.code())) {
                out.add("Question code '" + q.code() + "' is used twice.");
            }
        }
        for (QuestionView q : qs) {
            if (q == null || q.code() == null) continue;
            String at = "Question " + q.code() + ": ";
            if (blank(q.label())) out.add(at + "label is required.");
            else if (q.label().length() > 300) out.add(at + "label is longer than 300 characters.");
            AnswerType type = null;
            try { type = AnswerType.valueOf(q.answerType() == null ? "" : q.answerType()); }
            catch (IllegalArgumentException e) { out.add(at + "unknown answer type '" + q.answerType() + "'."); }
            if (type != null && CHOICE_TYPES.contains(type.name())) choiceOptions(at, q, out);
            if (q.min() != null && q.max() != null && q.min().compareTo(q.max()) > 0) out.add(at + "minimum is above maximum.");
            if (!blank(q.observationCode())) {
                if (observationCodes != null && !observationCodes.contains(q.observationCode())) {
                    out.add(at + "unknown observation type '" + q.observationCode() + "'.");
                }
                if (type != null && !(type == AnswerType.MEASUREMENT || type == AnswerType.NUMBER || type == AnswerType.DECIMAL)) {
                    out.add(at + "only MEASUREMENT, NUMBER and DECIMAL questions can feed an observation.");
                }
            }
            for (RuleView r : q.rules() == null ? List.<RuleView>of() : q.rules()) rule(at, groupCode, q.code(), r, codes, out);
        }

        Set<String> flagCodes = new HashSet<>();
        for (RedFlagView f : d.redFlags() == null ? List.<RedFlagView>of() : d.redFlags()) {
            if (f == null || blank(f.code()) || !CODE.matcher(f.code()).matches()) {
                out.add("Red flag code '" + (f == null ? "" : f.code()) + "' is not valid.");
                continue;
            }
            String at = "Red flag " + f.code() + ": ";
            if (!flagCodes.add(f.code())) out.add(at + "code is used twice.");
            if (blank(f.label())) out.add(at + "label is required.");
            if (!"INFO".equals(f.severity()) && !"URGENT".equals(f.severity())) out.add(at + "severity must be INFO or URGENT.");
            expression(at, f.expression(), codes, null, out);
        }
        return out;
    }

    private static void choiceOptions(String at, QuestionView q, List<String> out) {
        if (q.options() == null || q.options().isEmpty()) { out.add(at + "needs at least one option."); return; }
        Set<Object> seen = new HashSet<>();
        for (Map<String, Object> o : q.options()) {
            Object v = o == null ? null : o.get("value");
            Object l = o == null ? null : o.get("label");
            if (!(v instanceof String vs) || vs.isBlank()) { out.add(at + "every option needs a value."); continue; }
            if (!(l instanceof String ls) || ls.isBlank()) out.add(at + "option '" + vs + "' needs a label.");
            if (!seen.add(vs)) out.add(at + "option value '" + vs + "' is used twice.");
        }
    }

    private static void rule(String at, String groupCode, String own, RuleView r, Set<String> codes, List<String> out) {
        if (r == null || !RULE_KINDS.contains(r.kind())) {
            out.add(at + "unknown rule kind '" + (r == null ? null : r.kind()) + "'.");
            return;
        }
        String where = at + r.kind() + " rule: ";
        if ("TRIGGER_GROUP".equals(r.kind())) {
            if (blank(r.targetGroupCode())) out.add(where + "needs a group to open.");
            else if (r.targetGroupCode().equals(groupCode)) out.add(where + "a group cannot open itself.");
        } else if (!blank(r.targetGroupCode())) {
            out.add(where + "only TRIGGER_GROUP rules name a group to open.");
        }
        if ("WARNING_WHEN".equals(r.kind()) && blank(r.message())) out.add(where + "a warning needs its message.");
        expression(where, r.expression(), codes, "SHOW_WHEN".equals(r.kind()) ? own : null, out);
    }

    /** @param forbidden a question code the expression must not refer to (a question cannot depend on itself) */
    private static void expression(String where, Map<String, Object> expr, Set<String> codes, String forbidden, List<String> out) {
        try {
            QuestionRuleEngine.validateExpression(expr);
        } catch (IllegalArgumentException e) {
            out.add(where + e.getMessage());
            return;
        }
        Set<String> refs = new TreeSet<>();
        collect(expr, refs);
        for (String ref : refs) {
            if (!codes.contains(ref)) out.add(where + "refers to unknown question '" + ref + "'.");
            else if (ref.equals(forbidden)) out.add(where + "a question cannot depend on its own answer to be shown.");
        }
    }

    @SuppressWarnings("unchecked")
    private static void collect(Map<String, Object> expr, Set<String> refs) {
        for (String k : new String[]{"all", "any"}) {
            if (expr.get(k) instanceof List<?> l) for (Object o : l) if (o instanceof Map<?, ?> m) collect((Map<String, Object>) m, refs);
        }
        if (expr.get("not") instanceof Map<?, ?> m) collect((Map<String, Object>) m, refs);
        if (expr.get("q") instanceof String s) refs.add(s);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
