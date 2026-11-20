package za.co.handyflow.platform.legalpractice.application.internal;

import java.text.Normalizer;
import java.util.*;

/**
 * Decides whether two names (or two ID / registration numbers) are the same party for conflict-check purposes.
 * Deliberately cautious: a false alarm costs a partner a minute, a missed conflict can cost the firm a matter.
 * <ul>
 *   <li>EXACT_ID: the ID or registration numbers are the same (spaces, dashes and slashes ignored).</li>
 *   <li>EXACT_NAME: the names are the same once case, accents, punctuation, word order and company suffixes
 *       (Pty, Ltd, CC…) are ignored, so "Thabo Dlamini" and "DLAMINI, Thabo" match.</li>
 *   <li>SIMILAR: most of the words match, or all the words of the shorter name appear in the longer one
 *       (two or more words), so "Dlamini Trading" matches "Dlamini Trading and Logistics (Pty) Ltd".</li>
 * </ul>
 * Two shared words are the minimum for a similar match, so a single-word name only ever matches on an exact name:
 * one shared word ("Smith") proves nothing.
 */
public final class ConflictMatcher {

    public static final String EXACT_ID = "EXACT_ID";
    public static final String EXACT_NAME = "EXACT_NAME";
    public static final String SIMILAR = "SIMILAR";

    private static final Set<String> NOISE = Set.of("pty", "ltd", "limited", "inc", "cc", "the", "and", "of", "nv", "bv", "llc",
            "proprietary", "incorporated", "co", "company", "corporation", "corp", "npc", "soc", "rf");
    private static final double SIMILARITY = 0.6;

    private ConflictMatcher() {}

    /** Lower case, accents removed, punctuation to spaces, company-suffix words dropped. */
    public static List<String> tokens(String name) {
        if (name == null) return List.of();
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String t : s.replaceAll("[^a-z0-9]+", " ").trim().split(" ")) {
            if (!t.isEmpty() && !NOISE.contains(t)) out.add(t);
        }
        return out;
    }

    public static String normalizeId(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** The match kind, or empty when the two are not the same party. */
    public static Optional<String> compare(String nameA, String idA, String nameB, String idB) {
        String ia = normalizeId(idA), ib = normalizeId(idB);
        if (!ia.isEmpty() && ia.equals(ib)) return Optional.of(EXACT_ID);
        List<String> a = tokens(nameA), b = tokens(nameB);
        if (a.isEmpty() || b.isEmpty()) return Optional.empty();
        Set<String> sa = new TreeSet<>(a), sb = new TreeSet<>(b);
        if (sa.equals(sb)) return Optional.of(EXACT_NAME);
        Set<String> common = new TreeSet<>(sa);
        common.retainAll(sb);
        if (common.size() < 2) return Optional.empty();
        Set<String> union = new TreeSet<>(sa);
        union.addAll(sb);
        boolean contained = common.size() == Math.min(sa.size(), sb.size());
        if (contained || (double) common.size() / union.size() >= SIMILARITY) return Optional.of(SIMILAR);
        return Optional.empty();
    }
}
