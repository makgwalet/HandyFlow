package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggestions for filling gaps in an item, worked out from the rest of the tenant's own catalogue. There is no machine
 * learning and nothing leaves the system: it compares item names, looks at how the tenant already names SKUs and brands,
 * and at the usual margin of a category. A suggestion says why it was made and how sure it is. No Spring, no database.
 */
public final class EnrichmentRules {

    private EnrichmentRules() {}

    public static final String CATEGORY = "CATEGORY", BRAND = "BRAND", SKU = "SKU", PRICE = "PRICE";

    /** An item as the rules see it. */
    public record Candidate(UUID id, String name, UUID categoryId, String categoryName, String brand, String sku, BigDecimal price, BigDecimal cost) {}

    /** {@code value} is what applying would set (a category id, a brand, a SKU, or a price). Prices cannot be applied from here. */
    public record Suggestion(String kind, String value, String label, int confidence, String reason, boolean applicable) {}

    private static final Set<String> STOP = Set.of("the", "and", "of", "for", "with", "a", "an", "per", "pack", "box", "each", "set");
    private static final Pattern SIZE = Pattern.compile("^\\d+([.,]\\d+)?[a-z]{0,3}$");
    private static final Pattern SKU_SHAPE = Pattern.compile("^([A-Za-z]{2,6})([-_]?)(\\d{1,8})$");

    /** The words of a name that say what the thing is: lower case, no accents, no sizes or numbers, no filler words. */
    public static Set<String> tokens(String name) {
        if (name == null) return Set.of();
        String n = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        Set<String> out = new LinkedHashSet<>();
        for (String t : n.split("[^a-z0-9.,]+")) {
            String w = t.replaceAll("^[.,]+|[.,]+$", "");
            if (w.length() < 2 || STOP.contains(w) || SIZE.matcher(w).matches()) continue;
            out.add(w);
        }
        return out;
    }

    static double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int common = 0;
        for (String t : a) if (b.contains(t)) common++;
        return (double) common / (a.size() + b.size() - common);
    }

    /** A view of the whole catalogue that finds similar items quickly. Build once, ask many times. */
    public static final class Index {
        private final List<Candidate> all;
        private final Map<UUID, Set<String>> words = new HashMap<>();
        private final Map<String, List<Candidate>> byToken = new HashMap<>();
        private final Set<String> skus = new HashSet<>();
        private final Map<String, Integer> highest = new HashMap<>();   // upper-case prefix -> highest number in use
        private final Map<String, Integer> width = new HashMap<>();
        private final Map<String, String> separator = new HashMap<>();
        private final Map<String, String> brands = new LinkedHashMap<>();   // lower case -> as written

        public Index(List<Candidate> all) {
            this.all = all;
            for (Candidate c : all) {
                Set<String> w = tokens(c.name());
                words.put(c.id(), w);
                for (String t : w) byToken.computeIfAbsent(t, k -> new ArrayList<>()).add(c);
                if (c.sku() != null && !c.sku().isBlank()) {
                    skus.add(c.sku().trim().toUpperCase(Locale.ROOT));
                    Matcher m = SKU_SHAPE.matcher(c.sku().trim());
                    if (m.matches()) {
                        String p = m.group(1).toUpperCase(Locale.ROOT);
                        int n = Integer.parseInt(m.group(3));
                        highest.merge(p, n, Math::max);
                        width.merge(p, m.group(3).length(), Math::max);
                        separator.putIfAbsent(p, m.group(2));
                    }
                }
                if (c.brand() != null && !c.brand().isBlank()) brands.putIfAbsent(c.brand().trim().toLowerCase(Locale.ROOT), c.brand().trim());
            }
        }

        private record Near(Candidate item, double score) {}

        /** The most similar other items, best first (only those sharing at least half their words). */
        private List<Near> near(Candidate subject, int limit) {
            Set<String> mine = tokens(subject.name());
            Map<UUID, Candidate> seen = new LinkedHashMap<>();
            for (String t : mine) for (Candidate c : byToken.getOrDefault(t, List.of())) if (!c.id().equals(subject.id())) seen.put(c.id(), c);
            List<Near> out = new ArrayList<>();
            for (Candidate c : seen.values()) {
                double s = similarity(mine, words.get(c.id()));
                if (s >= 0.5) out.add(new Near(c, s));
            }
            out.sort(Comparator.comparingDouble((Near n) -> n.score).reversed().thenComparing(n -> n.item.name(), Comparator.nullsLast(String::compareTo)));
            return out.size() > limit ? out.subList(0, limit) : out;
        }

        /** Suggestions for what the item is missing. Empty when it is not missing anything we can guess. */
        public List<Suggestion> suggest(Candidate s, boolean includePrices) {
            List<Suggestion> out = new ArrayList<>();
            boolean noCategory = s.categoryId() == null, noBrand = blank(s.brand()), noSku = blank(s.sku());
            List<Near> near = (noCategory || noBrand || noSku) ? near(s, 5) : List.of();
            if (noCategory) category(near).ifPresent(out::add);
            if (noBrand) brand(s, near).ifPresent(out::add);
            if (noSku) sku(s, near).ifPresent(out::add);
            if (includePrices && (s.price() == null || s.price().signum() <= 0) && s.cost() != null && s.cost().signum() > 0) price(s).ifPresent(out::add);
            return out;
        }

        private Optional<Suggestion> category(List<Near> near) {
            Map<UUID, Double> weight = new HashMap<>();
            Map<UUID, String> label = new HashMap<>();
            Map<UUID, List<String>> who = new HashMap<>();
            double total = 0;
            for (Near n : near) {
                if (n.item.categoryId() == null) continue;
                weight.merge(n.item.categoryId(), n.score, Double::sum);
                label.put(n.item.categoryId(), n.item.categoryName());
                who.computeIfAbsent(n.item.categoryId(), k -> new ArrayList<>()).add(n.item.name());
                total += n.score;
            }
            if (weight.isEmpty()) return Optional.empty();
            UUID best = Collections.max(weight.entrySet(), Map.Entry.comparingByValue()).getKey();
            int confidence = (int) Math.round(100 * weight.get(best) / total);
            int supporters = who.get(best).size();
            double top = near.stream().filter(n -> best.equals(n.item.categoryId())).mapToDouble(n -> n.score).max().orElse(0);
            if (confidence < 60 || (supporters < 2 && top < 0.8)) return Optional.empty();
            return Optional.of(new Suggestion(CATEGORY, best.toString(), label.get(best), confidence,
                    "Similar items are in this category: " + String.join(", ", who.get(best).stream().limit(3).toList()) + ".", true));
        }

        private Optional<Suggestion> brand(Candidate s, List<Near> near) {
            Set<String> mine = tokens(s.name());
            // a brand the tenant already uses that appears in the name
            for (var b : brands.entrySet()) {
                Set<String> bt = tokens(b.getKey());
                if (!bt.isEmpty() && mine.containsAll(bt)) return Optional.of(new Suggestion(BRAND, b.getValue(), b.getValue(), 85, "The name contains the brand '" + b.getValue() + "', which you already use.", true));
            }
            Map<String, Integer> votes = new HashMap<>();
            Map<String, String> written = new HashMap<>();
            int withBrand = 0;
            for (Near n : near) {
                if (blank(n.item.brand())) continue;
                withBrand++;
                String k = n.item.brand().trim().toLowerCase(Locale.ROOT);
                votes.merge(k, 1, Integer::sum);
                written.putIfAbsent(k, n.item.brand().trim());
            }
            if (withBrand < 2) return Optional.empty();
            var best = Collections.max(votes.entrySet(), Map.Entry.comparingByValue());
            int confidence = (int) Math.round(100.0 * best.getValue() / withBrand);
            if (best.getValue() < 2 || confidence < 70) return Optional.empty();
            return Optional.of(new Suggestion(BRAND, written.get(best.getKey()), written.get(best.getKey()), confidence, best.getValue() + " similar items have this brand.", true));
        }

        private Optional<Suggestion> sku(Candidate s, List<Near> near) {
            // follow the pattern of the closest item that has one, e.g. CEM-014 -> CEM-015
            for (Near n : near) {
                if (blank(n.item.sku())) continue;
                Matcher m = SKU_SHAPE.matcher(n.item.sku().trim());
                if (!m.matches()) continue;
                String prefix = m.group(1).toUpperCase(Locale.ROOT);
                int next = highest.getOrDefault(prefix, 0) + 1;
                String sep = separator.getOrDefault(prefix, "");
                int w = width.getOrDefault(prefix, 1);
                String candidate = prefix + sep + String.format("%0" + w + "d", next);
                while (skus.contains(candidate.toUpperCase(Locale.ROOT))) { next++; candidate = prefix + sep + String.format("%0" + w + "d", next); }
                return Optional.of(new Suggestion(SKU, candidate, candidate, 70, "Follows your pattern '" + n.item.sku().trim() + "' (" + n.item.name() + ").", true));
            }
            return Optional.empty();
        }

        private Optional<Suggestion> price(Candidate s) {
            if (s.categoryId() == null) return Optional.empty();
            List<BigDecimal> margins = new ArrayList<>();
            for (Candidate c : all) {
                if (!s.categoryId().equals(c.categoryId()) || c.id().equals(s.id())) continue;
                BigDecimal m = ReportRules.margin(c.price(), c.cost());
                if (m != null && m.signum() > 0 && m.compareTo(BigDecimal.valueOf(95)) < 0) margins.add(m);
            }
            if (margins.size() < 3) return Optional.empty();
            Collections.sort(margins);
            BigDecimal median = margins.get(margins.size() / 2);
            BigDecimal price = s.cost().multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(100).subtract(median), 2, RoundingMode.HALF_UP);
            return Optional.of(new Suggestion(PRICE, price.toPlainString(), price.toPlainString(), 60,
                    "The usual margin in this category is " + median.toPlainString() + "% (from " + margins.size() + " items). Set the price on the item's Pricing tab.", false));
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
