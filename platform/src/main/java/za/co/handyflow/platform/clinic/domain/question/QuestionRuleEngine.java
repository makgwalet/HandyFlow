package za.co.handyflow.platform.clinic.domain.question;

import java.math.BigDecimal;
import java.util.*;

/**
 * Pure rule engine for the clinical question library (Q-3, Q-5). No I/O and no clinical knowledge:
 * it only reveals, requires, enables, warns and opens groups according to the expressions it is given.
 * It never produces a diagnosis.
 *
 * <h3>Expression format</h3>
 * <pre>
 *   {"all": [expr, ...]}        every sub-expression true
 *   {"any": [expr, ...]}        at least one true
 *   {"not": expr}
 *   {"q": "cough_days", "op": "GTE", "value": 14}     test an answer
 *   {"ctx": "ageMonths", "op": "LT", "value": 60}     test patient/visit context
 * </pre>
 * Operators: EQ, NE, GT, GTE, LT, LTE, IN (value is a list), CONTAINS, ANSWERED, EMPTY (no value).
 * An unanswered question makes every comparison false (including NE), so an empty form triggers nothing.
 *
 * <h3>Semantics</h3>
 * A question with SHOW_WHEN rules is visible only when all of them are true; otherwise its default applies.
 * Answers to hidden questions are ignored (dropped from the effective answers), repeated until stable,
 * so a stale answer can never keep another question or a flag alive.
 */
public final class QuestionRuleEngine {

    private static final Set<String> OPS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "IN", "CONTAINS", "ANSWERED", "EMPTY");
    private static final Set<String> NO_VALUE_OPS = Set.of("ANSWERED", "EMPTY");
    private static final Set<String> CTX_FIELDS = Set.of("ageMonths", "sexAtBirth", "pregnancyStatus", "visitType");

    private QuestionRuleEngine() {}

    public static EvaluationResult evaluate(List<QuestionDef> questions, List<RedFlagDef> redFlags,
                                            Map<String, Object> answers, RuleContext ctx) {
        RuleContext context = ctx == null ? RuleContext.empty() : ctx;
        Map<String, Object> given = answers == null ? Map.of() : answers;
        Set<String> known = new HashSet<>();
        for (QuestionDef q : questions) known.add(q.code());

        Map<String, Object> effective = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : given.entrySet()) if (known.contains(e.getKey())) effective.put(e.getKey(), e.getValue());

        Set<String> visible = new LinkedHashSet<>();
        for (int pass = 0; pass <= questions.size() + 1; pass++) {
            Set<String> next = new LinkedHashSet<>();
            for (QuestionDef q : questions) if (isVisible(q, effective, context)) next.add(q.code());
            Map<String, Object> narrowed = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : given.entrySet()) if (next.contains(e.getKey())) narrowed.put(e.getKey(), e.getValue());
            boolean stable = next.equals(visible) && narrowed.equals(effective);
            visible = next;
            effective = narrowed;
            if (stable) break;
        }

        Set<String> required = new LinkedHashSet<>();
        Set<String> disabled = new LinkedHashSet<>();
        Map<String, List<String>> warnings = new LinkedHashMap<>();
        Set<String> triggered = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();

        for (QuestionDef q : questions) {
            if (!visible.contains(q.code())) continue;
            boolean req = q.defaultRequired();
            boolean enableOk = true;
            for (RuleDef r : q.rules()) {
                switch (r.kind()) {
                    case REQUIRED_WHEN -> { if (matches(r.expression(), effective, context)) req = true; }
                    case ENABLE_WHEN   -> { if (!matches(r.expression(), effective, context)) enableOk = false; }
                    case WARNING_WHEN  -> {
                        if (matches(r.expression(), effective, context)) {
                            warnings.computeIfAbsent(q.code(), k -> new ArrayList<>())
                                    .add(r.message() == null ? "Check this answer." : r.message());
                        }
                    }
                    case TRIGGER_GROUP -> {
                        if (r.targetGroupCode() != null && matches(r.expression(), effective, context)) triggered.add(r.targetGroupCode());
                    }
                    case SHOW_WHEN -> { /* handled by isVisible */ }
                }
            }
            if (!enableOk) disabled.add(q.code());
            if (req) {
                required.add(q.code());
                if (enableOk && isEmpty(effective.get(q.code()))) missing.add(q.code());
            }
        }

        List<EvaluationResult.RedFlagResult> flags = new ArrayList<>();
        if (redFlags != null) {
            for (RedFlagDef f : redFlags) {
                if (matches(f.expression(), effective, context)) {
                    flags.add(new EvaluationResult.RedFlagResult(f.code(), f.label(), f.severity(), f.message()));
                }
            }
        }
        return new EvaluationResult(visible, required, disabled, warnings, triggered, flags, effective, missing);
    }

    private static boolean isVisible(QuestionDef q, Map<String, Object> answers, RuleContext ctx) {
        boolean hasShow = false;
        for (RuleDef r : q.rules()) {
            if (r.kind() == RuleKind.SHOW_WHEN) {
                hasShow = true;
                if (!matches(r.expression(), answers, ctx)) return false;
            }
        }
        return hasShow || q.defaultVisible();
    }

    // ── Expressions ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static boolean matches(Map<String, Object> expr, Map<String, Object> answers, RuleContext ctx) {
        if (expr == null || expr.isEmpty()) throw new IllegalArgumentException("A rule expression cannot be empty.");
        if (expr.containsKey("all")) {
            for (Object sub : asList(expr.get("all"))) if (!matches((Map<String, Object>) sub, answers, ctx)) return false;
            return true;
        }
        if (expr.containsKey("any")) {
            for (Object sub : asList(expr.get("any"))) if (matches((Map<String, Object>) sub, answers, ctx)) return true;
            return false;
        }
        if (expr.containsKey("not")) return !matches((Map<String, Object>) expr.get("not"), answers, ctx);

        String op = String.valueOf(expr.get("op"));
        Object actual;
        if (expr.containsKey("q")) actual = answers.get(String.valueOf(expr.get("q")));
        else if (expr.containsKey("ctx")) actual = ctx.get(String.valueOf(expr.get("ctx")));
        else throw new IllegalArgumentException("A rule needs 'q', 'ctx', 'all', 'any' or 'not'.");
        return test(op, actual, expr.get("value"));
    }

    /** Throws IllegalArgumentException with a readable reason when the expression is malformed. */
    @SuppressWarnings("unchecked")
    public static void validateExpression(Map<String, Object> expr) {
        if (expr == null || expr.isEmpty()) throw new IllegalArgumentException("A rule expression cannot be empty.");
        int forms = 0;
        for (String k : new String[]{"all", "any", "not", "q", "ctx"}) if (expr.containsKey(k)) forms++;
        if (forms != 1) throw new IllegalArgumentException("A rule must use exactly one of all, any, not, q or ctx.");
        if (expr.containsKey("all") || expr.containsKey("any")) {
            Object list = expr.containsKey("all") ? expr.get("all") : expr.get("any");
            List<Object> subs = asList(list);
            if (subs.isEmpty()) throw new IllegalArgumentException("'all'/'any' needs at least one sub-rule.");
            for (Object s : subs) {
                if (!(s instanceof Map)) throw new IllegalArgumentException("Sub-rules must be objects.");
                validateExpression((Map<String, Object>) s);
            }
            return;
        }
        if (expr.containsKey("not")) {
            if (!(expr.get("not") instanceof Map)) throw new IllegalArgumentException("'not' needs an object.");
            validateExpression((Map<String, Object>) expr.get("not"));
            return;
        }
        Object op = expr.get("op");
        if (!(op instanceof String) || !OPS.contains(op)) throw new IllegalArgumentException("Unknown operator '" + op + "'.");
        if (expr.containsKey("q") && !(expr.get("q") instanceof String s && !s.isBlank())) {
            throw new IllegalArgumentException("'q' must be a question code.");
        }
        if (expr.containsKey("ctx") && !CTX_FIELDS.contains(String.valueOf(expr.get("ctx")))) {
            throw new IllegalArgumentException("Unknown context field '" + expr.get("ctx") + "'.");
        }
        boolean needsValue = !NO_VALUE_OPS.contains(op);
        if (needsValue && !expr.containsKey("value")) throw new IllegalArgumentException("Operator " + op + " needs a value.");
        if ("IN".equals(op) && !(expr.get("value") instanceof List)) throw new IllegalArgumentException("IN needs a list value.");
    }

    // ── Operators ────────────────────────────────────────────────────────────

    private static boolean test(String op, Object actualRaw, Object expected) {
        Object actual = unwrap(actualRaw);
        switch (op) {
            case "ANSWERED": return !isEmpty(actual);
            case "EMPTY":    return isEmpty(actual);
            default: break;
        }
        if (isEmpty(actual)) return false;
        switch (op) {
            case "EQ":  return eq(actual, expected);
            case "NE":  return !eq(actual, expected);
            case "GT": case "GTE": case "LT": case "LTE": {
                BigDecimal a = number(actual), b = number(expected);
                if (a == null || b == null) return false;
                int c = a.compareTo(b);
                return switch (op) { case "GT" -> c > 0; case "GTE" -> c >= 0; case "LT" -> c < 0; default -> c <= 0; };
            }
            case "IN": {
                List<Object> options = asList(expected);
                if (actual instanceof List<?> l) { for (Object x : l) for (Object o : options) if (eq(x, o)) return true; return false; }
                for (Object o : options) if (eq(actual, o)) return true;
                return false;
            }
            case "CONTAINS": {
                if (actual instanceof List<?> l) { for (Object x : l) if (eq(x, expected)) return true; return false; }
                return String.valueOf(actual).toLowerCase().contains(String.valueOf(expected).toLowerCase());
            }
            default: throw new IllegalArgumentException("Unknown operator '" + op + "'.");
        }
    }

    private static boolean eq(Object a, Object b) {
        if (a instanceof List) return false;
        BigDecimal na = number(a), nb = number(b);
        if (na != null && nb != null && !(a instanceof Boolean) && !(b instanceof Boolean)) return na.compareTo(nb) == 0;
        return String.valueOf(a).equals(String.valueOf(b));
    }

    /** MEASUREMENT and DURATION answers are {value, unit}; rules compare the number. */
    private static Object unwrap(Object v) {
        if (v instanceof Map<?, ?> m && m.containsKey("value")) return m.get("value");
        return v;
    }

    static boolean isEmpty(Object v) {
        Object u = unwrap(v);
        if (u == null) return true;
        if (u instanceof String s) return s.isBlank();
        if (u instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    private static BigDecimal number(Object v) {
        if (v instanceof Boolean || v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        if (v instanceof String s) {
            try { return new BigDecimal(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        if (o instanceof List) return (List<Object>) o;
        throw new IllegalArgumentException("Expected a list.");
    }
}
