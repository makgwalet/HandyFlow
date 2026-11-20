package za.co.handyflow.platform.legalcompliance.application.internal;

import java.util.*;

/**
 * Explainable relevance matching: which of the tenant's own obligations, controls and policies does a piece of text seem to touch?
 *
 * This is word overlap, weighted so that rare words count for more than common ones (a word that appears in few of the tenant's items says more than one that appears in
 * many) and words in an item's title count double. If the text names an item's reference (for example "POPIA"), that is a strong signal on its own. Every suggestion lists
 * the words that matched, so a person can see why it was suggested and dismiss it. It does not read meaning, so it will miss synonyms and can match unrelated uses of a word.
 * Suggestions are a starting point for a human assessment, never a conclusion.
 */
public final class MatchRules {

    private MatchRules() {}

    public enum Strength { HIGH, MEDIUM, LOW }

    public record Candidate(String type, UUID id, String reference, String label, String text) {}

    public record Suggestion(String type, UUID id, String reference, String label, Strength strength, double score, boolean referenceMentioned, List<String> matchedTerms) {}

    private static final Set<String> STOP = Set.of("the", "and", "for", "with", "that", "this", "from", "are", "was", "were", "has", "have", "had", "not", "any", "all", "may", "shall", "must", "will",
            "per", "new", "into", "its", "their", "than", "then", "such", "other", "under", "over", "also", "which", "who", "whom", "where", "when", "been", "being", "can", "but", "out", "use",
            "act", "acts", "regulation", "regulations", "amendment", "amendments", "amended", "notice", "bill", "draft", "gazette", "section", "sections", "national", "south", "africa", "african",
            "republic", "government", "department", "minister", "public", "comment", "comments", "invited", "issued", "published", "effect", "date", "dates", "commencement");

    public static final int MAX_TERMS_SHOWN = 8, MIN_TOKEN = 3;

    public static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        for (String t : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim().split(" "))
            if (t.length() >= MIN_TOKEN && !STOP.contains(t)) out.add(t);
        return out;
    }

    public static List<Suggestion> suggest(String text, List<Candidate> corpus, int limit) {
        Set<String> query = tokens(text);
        String padded = " " + normalise(text) + " ";
        if (corpus.isEmpty() || (query.isEmpty() && padded.isBlank())) return List.of();

        int n = corpus.size();
        List<Set<String>> labelTokens = new ArrayList<>(), allTokens = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        for (Candidate c : corpus) {
            Set<String> lt = tokens(c.label()), at = new LinkedHashSet<>(lt);
            at.addAll(tokens(c.text()));
            labelTokens.add(lt); allTokens.add(at);
            for (String t : at) df.merge(t, 1, Integer::sum);
        }

        List<Suggestion> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Candidate c = corpus.get(i);
            Map<String, Double> hits = new LinkedHashMap<>();
            boolean labelHit = false;
            for (String t : query) {
                if (!allTokens.get(i).contains(t)) continue;
                double idf = Math.log(1.0 + (double) n / df.getOrDefault(t, 1));
                boolean inLabel = labelTokens.get(i).contains(t);
                labelHit |= inLabel;
                hits.put(t, idf * (inLabel ? 2 : 1));
            }
            boolean ref = mentions(padded, c.reference());
            if (hits.isEmpty() && !ref) continue;
            double score = hits.values().stream().mapToDouble(Double::doubleValue).sum() + (ref ? 5.0 : 0);
            Strength s = ref || (hits.size() >= 3 && labelHit) ? Strength.HIGH : hits.size() >= 2 ? Strength.MEDIUM : Strength.LOW;
            List<String> terms = hits.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).limit(MAX_TERMS_SHOWN).map(Map.Entry::getKey).toList();
            out.add(new Suggestion(c.type(), c.id(), c.reference(), c.label(), s, Math.round(score * 100.0) / 100.0, ref, terms));
        }
        out.sort(Comparator.comparingDouble(Suggestion::score).reversed().thenComparing(Suggestion::label, Comparator.nullsLast(Comparator.naturalOrder())));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static boolean mentions(String paddedText, String reference) {
        String r = normalise(reference);
        return r.length() >= 3 && paddedText.contains(" " + r + " ");
    }

    private static String normalise(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim(); }
}
