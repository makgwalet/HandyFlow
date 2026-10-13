package za.co.handyflow.platform.debtcollection.application.internal;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {{placeholder}} substitution. Strict by design: an unresolved placeholder throws instead of sending
 * "Dear {{debtorName}}" to a debtor. Built-in placeholders come from the case; anything else must be
 * namespaced "custom." and is supplied by the caller (so a caller cannot spoof amounts or case numbers).
 */
public final class TemplateRenderer {

    public static final Set<String> BUILT_IN = Set.of("debtorName", "caseNumber", "totalOutstanding", "openedDate");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");

    private TemplateRenderer() {}

    public static Set<String> placeholders(String template) {
        Set<String> out = new LinkedHashSet<>();
        if (template == null) return out;
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** True if every placeholder is built-in or "custom."-namespaced — i.e. the template can ever render. */
    public static boolean isRenderable(String template) {
        return placeholders(template).stream().allMatch(p -> BUILT_IN.contains(p) || p.startsWith("custom."));
    }

    public static String render(String template, Map<String, String> vars) {
        if (template == null) return null;
        Set<String> missing = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars.get(m.group(1));
            if (v == null) {
                missing.add(m.group(1));
                m.appendReplacement(sb, "");
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(v));
            }
        }
        m.appendTail(sb);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Template placeholders without a value: " + missing);
        }
        return sb.toString();
    }
}
