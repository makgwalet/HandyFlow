package za.co.handyflow.platform.crm.application.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Finds customers that are probably the same business or person entered twice. Pure logic with no database or Spring, so it can be
 * tested on its own. Two customers are linked when they share a phone number, a VAT number, or a name (ignoring "Pty", "Ltd", "CC" and
 * the like), or when their names are very close. Linked customers are then joined into groups. A pair a person has dismissed as
 * "not a duplicate" does not link on its own.
 */
public final class DuplicateMatcher {

    public record Candidate(UUID id, String name, String phone, String taxNumber) {}

    /** ids are in the order given to find(); reasons are in a fixed order. */
    public record Group(List<UUID> ids, List<String> reasons) {}

    public static final String SAME_PHONE = "Same phone number";
    public static final String SAME_TAX = "Same VAT number";
    public static final String SAME_NAME = "Same name";
    public static final String SIMILAR_NAME = "Very similar name";
    private static final List<String> REASON_ORDER = List.of(SAME_TAX, SAME_PHONE, SAME_NAME, SIMILAR_NAME);

    /** A number or name shared by more than this many customers is a switchboard or a generic name, not evidence of a duplicate. */
    static final int MAX_SHARED = 20;
    /** Comparing every pair of names is quadratic, so very large tenants skip the "very similar" check. */
    public static final int MAX_FUZZY = 2000;

    private static final Set<String> NAME_NOISE = Set.of("pty", "ltd", "limited", "cc", "inc", "incorporated", "proprietary", "co", "company", "the", "and");

    private DuplicateMatcher() {}

    public static List<Group> find(List<Candidate> candidates, Set<String> dismissedPairs, BiPredicate<String, String> similar) {
        Map<String, Set<String>> edgeReasons = new HashMap<>();   // pair key -> reasons
        Map<String, UUID[]> edgeIds = new HashMap<>();

        bucket(candidates, c -> normalisePhone(c.phone()), SAME_PHONE, edgeReasons, edgeIds);
        bucket(candidates, c -> normaliseTax(c.taxNumber()), SAME_TAX, edgeReasons, edgeIds);
        bucket(candidates, c -> { String n = normaliseName(c.name()); return n.length() >= 3 ? n : null; }, SAME_NAME, edgeReasons, edgeIds);

        if (candidates.size() <= MAX_FUZZY) {
            Map<String, List<Candidate>> byStart = new HashMap<>();
            for (Candidate c : candidates) {
                String n = normaliseName(c.name());
                if (n.length() >= 5) byStart.computeIfAbsent(n.substring(0, 2), k -> new ArrayList<>()).add(c);
            }
            for (List<Candidate> list : byStart.values()) {
                for (int i = 0; i < list.size(); i++) {
                    for (int j = i + 1; j < list.size(); j++) {
                        String a = normaliseName(list.get(i).name()), b = normaliseName(list.get(j).name());
                        if (!a.equals(b) && similar.test(a, b)) addEdge(list.get(i).id(), list.get(j).id(), SIMILAR_NAME, edgeReasons, edgeIds);
                    }
                }
            }
        }

        // Join linked customers into groups (union-find), leaving out pairs that were dismissed.
        Map<UUID, UUID> parent = new HashMap<>();
        for (var e : edgeIds.entrySet()) {
            if (dismissedPairs.contains(e.getKey())) continue;
            union(parent, e.getValue()[0], e.getValue()[1]);
        }
        Map<UUID, List<UUID>> members = new HashMap<>();
        Map<UUID, Set<String>> reasons = new HashMap<>();
        for (Candidate c : candidates) {
            if (!parent.containsKey(c.id())) continue;
            members.computeIfAbsent(root(parent, c.id()), k -> new ArrayList<>()).add(c.id());
        }
        for (var e : edgeIds.entrySet()) {
            if (dismissedPairs.contains(e.getKey())) continue;
            reasons.computeIfAbsent(root(parent, e.getValue()[0]), k -> new LinkedHashSet<>()).addAll(edgeReasons.get(e.getKey()));
        }
        List<Group> groups = new ArrayList<>();
        for (var e : members.entrySet()) {
            if (e.getValue().size() < 2) continue;
            List<String> why = REASON_ORDER.stream().filter(reasons.getOrDefault(e.getKey(), Set.of())::contains).toList();
            groups.add(new Group(e.getValue(), why));
        }
        // Strongest evidence first, then biggest group, then a stable order.
        groups.sort(Comparator.comparingInt((Group g) -> REASON_ORDER.indexOf(g.reasons().get(0)))
                .thenComparing(g -> -g.ids().size()).thenComparing(g -> g.ids().get(0).toString()));
        return groups;
    }

    /** The key used to remember that two customers were dismissed as not duplicates. Order does not matter. */
    public static String pairKey(UUID a, UUID b) {
        String x = a.toString(), y = b.toString();
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    // ── normalising ────────────────────────────────────────────────────────

    /** Lower case, punctuation removed, and company endings such as Pty, Ltd and CC dropped: "Tau Mining (Pty) Ltd" becomes "tau mining". */
    public static String normaliseName(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String token : name.toLowerCase().replace("&", " and ").replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+")) {
            if (token.isEmpty() || NAME_NOISE.contains(token)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(token);
        }
        return sb.toString();
    }

    /** The last nine digits, so "082 123 4567", "0821234567" and "+27 82 123 4567" match. Null when there are too few digits to mean anything. */
    public static String normalisePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        return digits.length() < 9 ? null : digits.substring(digits.length() - 9);
    }

    /** Letters and digits only, lower case. Null when too short to mean anything. */
    public static String normaliseTax(String tax) {
        if (tax == null) return null;
        String t = tax.toLowerCase().replaceAll("[^a-z0-9]", "");
        return t.length() < 5 ? null : t;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static void bucket(List<Candidate> cs, java.util.function.Function<Candidate, String> key, String reason,
                               Map<String, Set<String>> edgeReasons, Map<String, UUID[]> edgeIds) {
        Map<String, List<Candidate>> buckets = new HashMap<>();
        for (Candidate c : cs) {
            String k = key.apply(c);
            if (k != null) buckets.computeIfAbsent(k, x -> new ArrayList<>()).add(c);
        }
        for (List<Candidate> list : buckets.values()) {
            if (list.size() < 2 || list.size() > MAX_SHARED) continue;
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) addEdge(list.get(i).id(), list.get(j).id(), reason, edgeReasons, edgeIds);
            }
        }
    }

    private static void addEdge(UUID a, UUID b, String reason, Map<String, Set<String>> edgeReasons, Map<String, UUID[]> edgeIds) {
        String k = pairKey(a, b);
        edgeReasons.computeIfAbsent(k, x -> new TreeSet<>()).add(reason);
        edgeIds.putIfAbsent(k, new UUID[]{a, b});
    }

    private static UUID root(Map<UUID, UUID> parent, UUID x) {
        parent.putIfAbsent(x, x);
        UUID r = x;
        while (!parent.get(r).equals(r)) r = parent.get(r);
        while (!parent.get(x).equals(r)) { UUID next = parent.get(x); parent.put(x, r); x = next; }
        return r;
    }

    private static void union(Map<UUID, UUID> parent, UUID a, UUID b) {
        UUID ra = root(parent, a), rb = root(parent, b);
        if (!ra.equals(rb)) parent.put(ra, rb);
    }
}
