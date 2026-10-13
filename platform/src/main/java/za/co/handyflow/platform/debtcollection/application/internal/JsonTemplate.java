package za.co.handyflow.platform.debtcollection.application.internal;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {{name}} placeholders in a JSON body template with values escaped for a JSON string context, so a message
 * containing quotes, backslashes or newlines cannot break out of the string or alter the request structure.
 * Placeholders must sit INSIDE JSON string quotes in the template, e.g. {"to":"{{to}}","text":"{{body}}"}.
 * An unknown placeholder is an error, never silently emitted.
 */
final class JsonTemplate {

    private static final Pattern P = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private JsonTemplate() {}

    static String render(String template, Map<String, String> vars) {
        Matcher m = P.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars.get(m.group(1));
            if (v == null) throw new IllegalArgumentException("Unknown placeholder {{" + m.group(1) + "}} in SMS body template");
            m.appendReplacement(sb, Matcher.quoteReplacement(escape(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }
}
