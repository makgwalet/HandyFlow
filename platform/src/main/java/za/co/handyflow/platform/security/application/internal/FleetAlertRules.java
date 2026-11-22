package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * When to tell people about a phone problem. A problem is alerted once when it appears, reminded after
 * {@link #REMIND_AFTER} while it lasts, and forgotten when it clears (so a later return alerts again). Only urgent
 * problems alert; low battery and old app versions stay on the screen. Pure logic.
 */
final class FleetAlertRules {

    static final Duration REMIND_AFTER = Duration.ofHours(6);
    static final Set<String> ALERTABLE = Set.of("OFFLINE", "CRITICAL_BATTERY");

    record Decision(Set<String> alertNow, Set<String> newAlerted, boolean changed) {}

    private FleetAlertRules() {}

    static Decision decide(Collection<String> current, Set<String> alreadyAlerted, Instant alertedAt, Instant now) {
        Set<String> alertable = current.stream().filter(ALERTABLE::contains).collect(Collectors.toCollection(TreeSet::new));
        Set<String> kept = new TreeSet<>(alreadyAlerted);
        kept.retainAll(alertable);                                   // cleared problems are forgotten
        Set<String> fresh = new TreeSet<>(alertable);
        fresh.removeAll(alreadyAlerted);                             // new problems
        boolean remind = alertedAt != null && !kept.isEmpty() && !alertedAt.plus(REMIND_AFTER).isAfter(now);
        Set<String> alertNow = new TreeSet<>(fresh);
        if (remind) alertNow.addAll(kept);
        Set<String> newAlerted = alertNow.isEmpty() ? kept : alertable;
        boolean changed = !alertNow.isEmpty() || !newAlerted.equals(alreadyAlerted);
        return new Decision(alertNow, newAlerted, changed);
    }

    static Set<String> parse(String stored) {
        if (stored == null || stored.isBlank()) return new TreeSet<>();
        return java.util.Arrays.stream(stored.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
    }

    static String format(Set<String> issues) {
        return issues.isEmpty() ? null : String.join(",", new TreeSet<>(issues));
    }

    // ---- wording ----
    record Line(String phone, String guard, Set<String> issues) {}
    record Text(String title, String message) {}

    static final int MAX_LINES = 5;

    static String issueWords(String code) {
        return switch (code) {
            case "OFFLINE" -> "silent while on shift";
            case "CRITICAL_BATTERY" -> "battery almost flat";
            default -> code.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        };
    }

    static Text digest(List<Line> lines) {
        int n = lines.size();
        String title = (n == 1 ? "1 phone needs" : n + " phones need") + " attention";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, MAX_LINES); i++) {
            Line l = lines.get(i);
            String phone = l.phone() == null || l.phone().isBlank() ? "Unnamed phone" : l.phone().trim();
            sb.append(i == 0 ? "" : "\n").append(phone);
            if (l.guard() != null && !l.guard().isBlank()) sb.append(" (").append(l.guard().trim()).append(")");
            sb.append(": ").append(new LinkedHashSet<>(l.issues().stream().map(FleetAlertRules::issueWords).toList())
                    .stream().collect(Collectors.joining(", ")));
        }
        if (n > MAX_LINES) sb.append("\nand ").append(n - MAX_LINES).append(" more");
        return new Text(title, sb.toString());
    }
}
