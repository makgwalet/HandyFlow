package za.co.handyflow.platform.legalcompliance.application.internal;

import java.util.*;

/**
 * Pure, stateless comparison of two versions' requirements.
 * <ul>
 *  <li>A new requirement matches an old one through previousId (set when carried forward); if it has none,
 *      through the same reference (case-insensitive), so a hand-rebuilt draft does not look like "everything removed and re-added".</li>
 *  <li>Matched pairs are UNCHANGED or CHANGED. Text is compared after collapsing whitespace, so reflowing a paragraph is not a change.
 *      Category, frequency, min employees, industries, activities and evidence expectation are all compared.</li>
 *  <li>Unmatched new = ADDED. Unmatched old = REMOVED.</li>
 * </ul>
 */
public final class ChangeDetectionRules {

    private ChangeDetectionRules() {}

    public enum Kind { ADDED, CHANGED, REMOVED, UNCHANGED }

    public record Req(UUID id, UUID previousId, String reference, String title, String text, String category, String recurrence,
                      Integer minEmployees, List<String> industries, List<String> activities, String evidence) {}

    public record Delta(Kind kind, UUID oldId, UUID newId, String reference, String title, List<String> changedFields) {
        public String summary() { return changedFields.isEmpty() ? null : String.join("; ", changedFields); }
    }

    public static List<Delta> diff(List<Req> oldReqs, List<Req> newReqs) {
        Map<UUID, Req> oldById = new LinkedHashMap<>();
        oldReqs.forEach(o -> oldById.put(o.id(), o));
        Map<String, Req> oldByRef = new HashMap<>();
        oldReqs.forEach(o -> oldByRef.put(key(o.reference()), o));

        Set<UUID> matchedOld = new HashSet<>();
        List<Delta> out = new ArrayList<>();

        for (Req n : newReqs) {
            Req o = n.previousId() != null ? oldById.get(n.previousId()) : null;
            if (o == null || matchedOld.contains(o.id())) {
                Req byRef = oldByRef.get(key(n.reference()));
                o = byRef != null && !matchedOld.contains(byRef.id()) ? byRef : null;
            }
            if (o == null) {
                out.add(new Delta(Kind.ADDED, null, n.id(), n.reference(), n.title(), List.of()));
                continue;
            }
            matchedOld.add(o.id());
            List<String> changes = changes(o, n);
            out.add(new Delta(changes.isEmpty() ? Kind.UNCHANGED : Kind.CHANGED, o.id(), n.id(), n.reference(), n.title(), changes));
        }
        for (Req o : oldReqs)
            if (!matchedOld.contains(o.id()))
                out.add(new Delta(Kind.REMOVED, o.id(), null, o.reference(), o.title(), List.of()));
        return out;
    }

    static List<String> changes(Req o, Req n) {
        List<String> c = new ArrayList<>();
        if (!norm(o.text()).equals(norm(n.text()))) c.add("Requirement text changed");
        if (!norm(o.title()).equals(norm(n.title()))) c.add("Title changed");
        if (!Objects.equals(o.category(), n.category())) c.add("Category " + o.category() + " → " + n.category());
        if (!Objects.equals(o.recurrence(), n.recurrence())) c.add("Frequency " + o.recurrence() + " → " + n.recurrence());
        if (!Objects.equals(o.minEmployees(), n.minEmployees())) c.add("Employee threshold " + show(o.minEmployees()) + " → " + show(n.minEmployees()));
        if (!sameSet(o.industries(), n.industries())) c.add("Industries " + o.industries() + " → " + n.industries());
        if (!sameSet(o.activities(), n.activities())) c.add("Activities " + o.activities() + " → " + n.activities());
        if (!norm(o.evidence()).equals(norm(n.evidence()))) c.add("Evidence expectation changed");
        return c;
    }

    private static String show(Integer i) { return i == null ? "none" : i.toString(); }
    private static String key(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static String norm(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }
    private static boolean sameSet(List<String> a, List<String> b) {
        return new HashSet<>(a == null ? List.of() : a).equals(new HashSet<>(b == null ? List.of() : b));
    }
}
