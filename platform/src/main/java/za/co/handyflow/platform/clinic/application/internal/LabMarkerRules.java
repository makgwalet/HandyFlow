package za.co.handyflow.platform.clinic.application.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure rules for lab markers typed in from a lab report. The reference range and critical limits are whatever the lab
 * printed; nothing here knows what is normal for any test. Critical means "at or beyond the lab's critical limit" (the
 * cautious reading), or the clinician marked it CRITICAL.
 */
final class LabMarkerRules {

    static final int MAX_MARKERS = 100;
    static final int MAX_NAME = 100;
    static final int MAX_VALUE = 50;
    static final int MAX_UNIT = 30;
    static final Set<String> CLINICIAN_FLAGS = Set.of("NORMAL", "LOW", "HIGH", "ABNORMAL", "CRITICAL");

    /** What the clinician typed for one marker. */
    record Input(String marker, String value, String unit, BigDecimal refLow, BigDecimal refHigh,
                 BigDecimal criticalLow, BigDecimal criticalHigh, String flag) {}

    /** A marker as stored and shown, with its worked-out flag: NORMAL, LOW, HIGH, ABNORMAL, CRITICAL or UNKNOWN. */
    record Evaluated(String marker, String value, String unit, String refRange, String flag,
                     BigDecimal refLow, BigDecimal refHigh, BigDecimal criticalLow, BigDecimal criticalHigh) {}

    /** The tidy list plus the two roll-up flags. */
    record Outcome(List<Evaluated> markers, boolean anyAbnormal, boolean anyCritical) {
        String json() { return LabMarkerRules.toJson(markers); }
    }

    private LabMarkerRules() {}

    static Outcome evaluate(List<Input> inputs) {
        if (inputs == null) inputs = List.of();
        if (inputs.size() > MAX_MARKERS) throw new IllegalArgumentException("At most " + MAX_MARKERS + " markers per result");
        List<Evaluated> out = new ArrayList<>();
        boolean abnormal = false, critical = false;
        int line = 0;
        for (Input in : inputs) {
            line++;
            Evaluated e = one(in, line);
            out.add(e);
            if (!"NORMAL".equals(e.flag()) && !"UNKNOWN".equals(e.flag())) abnormal = true;
            if ("CRITICAL".equals(e.flag())) critical = true;
        }
        return new Outcome(out, abnormal, critical);
    }

    private static Evaluated one(Input in, int line) {
        String where = "Marker " + line + ": ";
        String name = clean(in.marker());
        String value = clean(in.value());
        String unit = clean(in.unit());
        if (name == null) throw new IllegalArgumentException(where + "enter the test name");
        if (value == null) throw new IllegalArgumentException(where + "enter the result for " + name);
        if (name.length() > MAX_NAME) throw new IllegalArgumentException(where + "the test name is too long (at most " + MAX_NAME + " characters)");
        if (value.length() > MAX_VALUE) throw new IllegalArgumentException(where + "the result is too long (at most " + MAX_VALUE + " characters)");
        if (unit != null && unit.length() > MAX_UNIT) throw new IllegalArgumentException(where + "the unit is too long (at most " + MAX_UNIT + " characters)");

        String clinician = in.flag() == null || in.flag().isBlank() ? null : in.flag().trim().toUpperCase(java.util.Locale.ROOT);
        if (clinician != null && !CLINICIAN_FLAGS.contains(clinician)) throw new IllegalArgumentException(where + "unknown flag " + in.flag());

        BigDecimal rl = in.refLow(), rh = in.refHigh(), cl = in.criticalLow(), ch = in.criticalHigh();
        if (rl != null && rh != null && rl.compareTo(rh) > 0) throw new IllegalArgumentException(where + name + ": the lower reference limit is above the upper one");
        if (cl != null && rl != null && cl.compareTo(rl) > 0) throw new IllegalArgumentException(where + name + ": the lower critical limit is above the lower reference limit");
        if (ch != null && rh != null && ch.compareTo(rh) < 0) throw new IllegalArgumentException(where + name + ": the upper critical limit is below the upper reference limit");
        if (cl != null && ch != null && cl.compareTo(ch) >= 0) throw new IllegalArgumentException(where + name + ": the critical limits overlap");

        String computed = computeFlag(parse(value), rl, rh, cl, ch);
        String flag = moreSevere(computed, clinician);
        return new Evaluated(name, value, unit, refRange(rl, rh), flag, rl, rh, cl, ch);
    }

    /** Null when the text is not a plain number. Accepts a decimal comma. */
    static BigDecimal parse(String value) {
        if (value == null) return null;
        String v = value.trim().replace(',', '.');
        if (v.isEmpty()) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** UNKNOWN when the result is not a number, or is a number with no reference range to judge it against. */
    static String computeFlag(BigDecimal v, BigDecimal rl, BigDecimal rh, BigDecimal cl, BigDecimal ch) {
        if (v == null) return "UNKNOWN";
        if ((cl != null && v.compareTo(cl) <= 0) || (ch != null && v.compareTo(ch) >= 0)) return "CRITICAL";
        if (rl == null && rh == null) return "UNKNOWN";
        if (rl != null && v.compareTo(rl) < 0) return "LOW";
        if (rh != null && v.compareTo(rh) > 0) return "HIGH";
        return "NORMAL";
    }

    private static int severity(String flag) {
        if (flag == null || "UNKNOWN".equals(flag)) return -1;
        return switch (flag) {
            case "NORMAL" -> 0;
            case "LOW", "HIGH", "ABNORMAL" -> 1;
            case "CRITICAL" -> 2;
            default -> -1;
        };
    }

    /** The worse of what was worked out and what the clinician said; a clinician flag fills in when nothing could be worked out. */
    static String moreSevere(String computed, String clinician) {
        return severity(clinician) > severity(computed) ? clinician : computed;
    }

    static String refRange(BigDecimal low, BigDecimal high) {
        if (low != null && high != null) return low.stripTrailingZeros().toPlainString() + "–" + high.stripTrailingZeros().toPlainString();
        if (low != null) return "≥ " + low.stripTrailingZeros().toPlainString();
        if (high != null) return "≤ " + high.stripTrailingZeros().toPlainString();
        return null;
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    // ── JSON (kept by hand so the rules stay free of libraries) ───────────────

    static String toJson(List<Evaluated> markers) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < markers.size(); i++) {
            Evaluated m = markers.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            sb.append("\"marker\":").append(str(m.marker()));
            sb.append(",\"value\":").append(str(m.value()));
            sb.append(",\"unit\":").append(str(m.unit()));
            sb.append(",\"refRange\":").append(str(m.refRange()));
            sb.append(",\"flag\":").append(str(m.flag()));
            sb.append(",\"refLow\":").append(num(m.refLow()));
            sb.append(",\"refHigh\":").append(num(m.refHigh()));
            sb.append(",\"criticalLow\":").append(num(m.criticalLow()));
            sb.append(",\"criticalHigh\":").append(num(m.criticalHigh()));
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    private static String num(BigDecimal n) {
        return n == null ? "null" : n.toPlainString();
    }

    private static String str(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
