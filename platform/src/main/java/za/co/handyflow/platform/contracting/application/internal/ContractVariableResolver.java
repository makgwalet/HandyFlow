package za.co.handyflow.platform.contracting.application.internal;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {{variable}} placeholders in contract body templates.
 *
 * Improvements over original:
 * 1. findUnresolved() — returns all remaining {{token}} patterns so the service can
 *    reject sending if any variables are still outstanding.
 * 2. Date formatting — {{variable|date}} formats LocalDate values as "d MMMM yyyy"
 *    instead of the raw ISO string "2025-01-15".
 * 3. Number formatting — {{variable|currency}} formats numbers as "R 12,500.00".
 * 4. Defensive null handling — null variable values are replaced with "" not "null".
 */
@Component
public class ContractVariableResolver {

    private static final Pattern VAR_PATTERN    = Pattern.compile("\\{\\{([^}|]+)(?:\\|([^}]+))?}}");
    private static final Pattern UNRESOLVED     = Pattern.compile("\\{\\{[^}]+}}");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    /**
     * Substitutes all {{variable}} and {{variable|format}} placeholders.
     * Always substitutes {{date}} with today in human-readable format.
     */
    public String resolve(String template, Map<String, String> variables) {
        if (template == null) return "";

        StringBuffer result = new StringBuffer();
        Matcher m = VAR_PATTERN.matcher(template);

        while (m.find()) {
            String key    = m.group(1).trim();
            String format = m.group(2) != null ? m.group(2).trim() : null;

            String value;
            if ("date".equals(key) && (variables == null || !variables.containsKey("date"))) {
                value = LocalDate.now().format(DATE_FMT);
            } else if (variables != null && variables.containsKey(key)) {
                String raw = variables.get(key);
                raw = raw != null ? raw : "";
                value = applyFormat(raw, format);
            } else {
                // Leave unresolved — findUnresolved() will catch these before sending
                value = m.group(0);
            }
            m.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        m.appendTail(result);
        return result.toString();
    }

    /**
     * Returns all {{variable}} placeholders still present in the text.
     * Called by ContractingService.sendForSigning() to block sending incomplete contracts.
     */
    public List<String> findUnresolved(String body) {
        if (body == null) return List.of();
        List<String> found = new ArrayList<>();
        Matcher m = UNRESOLVED.matcher(body);
        while (m.find()) found.add(m.group());
        return found;
    }

    // ── Format modifiers ──────────────────────────────────────────────────────

    private String applyFormat(String value, String format) {
        if (format == null || value.isBlank()) return value;
        return switch (format.toLowerCase()) {
            case "date" -> formatDate(value);
            case "currency" -> formatCurrency(value);
            case "upper" -> value.toUpperCase();
            case "lower" -> value.toLowerCase();
            default -> value;
        };
    }

    private String formatDate(String iso) {
        try {
            return LocalDate.parse(iso).format(DATE_FMT);
        } catch (Exception e) {
            return iso; // fallback to raw if not a valid date
        }
    }

    private String formatCurrency(String number) {
        try {
            double d = Double.parseDouble(number);
            return String.format("R %,.2f", d);
        } catch (Exception e) {
            return number;
        }
    }
}
