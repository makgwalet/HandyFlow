package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Finds records that need attention: missing basics, prices that make no sense, drafts left behind, and items that look like
 * the same product entered twice. Pure, so it can be tested without a database; the service feeds it the items.
 */
public final class DataQualityRules {
    private DataQualityRules() {}

    public enum Kind {
        POSSIBLE_DUPLICATE("Possible duplicates", "Two items that look like the same product. Keep one and archive the other, or give them clearly different names.", false),
        ZERO_PRICE("Active with no price", "An item on sale at R 0.00. Set its price, or suspend it.", false),
        BELOW_COST("Price below cost", "Selling for less than it costs. Check the price or the cost.", true),
        BELOW_MIN_PRICE("Price below the minimum", "The price is under the minimum set for the item.", true),
        MISSING_CATEGORY("No category", "Add a category so the item can be found and reported on.", false),
        MISSING_SKU("No SKU", "Add a SKU so the item can be imported, updated and scanned.", false),
        MISSING_BARCODE("Goods with no barcode", "Goods that are scanned or stocked need a barcode.", false),
        MISSING_DESCRIPTION("No description", "A short description helps staff and customers pick the right item.", false),
        STALE_DRAFT("Drafts left unfinished", "A draft nobody has touched for a month. Finish and activate it, or delete it.", false);

        public final String label, advice;
        /** True when the finding reveals cost or minimum price, so only people allowed to see prices get it. */
        public final boolean needsPriceAccess;
        Kind(String label, String advice, boolean needsPriceAccess) { this.label = label; this.advice = advice; this.needsPriceAccess = needsPriceAccess; }
    }

    public static final int STALE_DRAFT_DAYS = 30, DUPLICATE_BLOCK_LIMIT = 400;
    public static final double SIMILARITY = 0.94;

    /** What the rules need to know about one item. */
    public record Row(UUID id, String name, String sku, String barcode, String brand, String description, boolean hasCategory, CatalogueItemType type,
                      CatalogueItemStatus status, BigDecimal price, BigDecimal cost, BigDecimal minPrice, boolean isVariant, boolean hasVariants, Instant updatedAt) {}

    public record Finding(Kind kind, UUID itemId, String detail) {}

    public static List<Finding> evaluate(Collection<Row> rows, Instant now) {
        List<Finding> out = new ArrayList<>();
        List<Row> duplicateCandidates = new ArrayList<>();
        for (Row r : rows) {
            if (r.status() == CatalogueItemStatus.ARCHIVED) continue;
            boolean live = r.status() == CatalogueItemStatus.ACTIVE || r.status() == CatalogueItemStatus.DRAFT || r.status() == CatalogueItemStatus.SUSPENDED;
            if (r.status() == CatalogueItemStatus.DRAFT && r.updatedAt() != null && Duration.between(r.updatedAt(), now).toDays() >= STALE_DRAFT_DAYS)
                out.add(new Finding(Kind.STALE_DRAFT, r.id(), "Last changed " + Duration.between(r.updatedAt(), now).toDays() + " days ago."));
            if (!live || r.hasVariants()) continue;   // a parent is sold through its variants; its own price and codes do not matter
            if (!r.isVariant()) duplicateCandidates.add(r);
            if (r.status() == CatalogueItemStatus.ACTIVE && r.price() != null && r.price().signum() <= 0) out.add(new Finding(Kind.ZERO_PRICE, r.id(), "The price is zero."));
            if (r.price() != null && r.cost() != null && r.cost().signum() > 0 && r.price().compareTo(r.cost()) < 0)
                out.add(new Finding(Kind.BELOW_COST, r.id(), "Price " + r.price().toPlainString() + " is below cost " + r.cost().toPlainString() + "."));
            if (r.price() != null && r.minPrice() != null && r.price().compareTo(r.minPrice()) < 0)
                out.add(new Finding(Kind.BELOW_MIN_PRICE, r.id(), "Price " + r.price().toPlainString() + " is below the minimum " + r.minPrice().toPlainString() + "."));
            if (!r.isVariant() && !r.hasCategory()) out.add(new Finding(Kind.MISSING_CATEGORY, r.id(), "No category."));
            if (blank(r.sku())) out.add(new Finding(Kind.MISSING_SKU, r.id(), "No SKU."));
            if (CompletenessRules.isGoods(r.type()) && blank(r.barcode())) out.add(new Finding(Kind.MISSING_BARCODE, r.id(), "No barcode."));
            if (!r.isVariant() && blank(r.description())) out.add(new Finding(Kind.MISSING_DESCRIPTION, r.id(), "No description."));
        }
        out.addAll(duplicates(duplicateCandidates));
        return out;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    // ── Duplicates ─────────────────────────────────────────────────────────────

    /** "Cement 50 kg." and "CEMENT 50KG" are the same once case, accents, spacing and punctuation are removed. */
    public static String normalise(String name) {
        if (name == null) return "";
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return s.replaceAll("[^a-z0-9]+", " ").trim();
    }

    static List<Finding> duplicates(List<Row> rows) {
        Map<String, List<Row>> blocks = new HashMap<>();
        for (Row r : rows) {
            String n = normalise(r.name());
            if (n.isEmpty()) continue;
            blocks.computeIfAbsent(n.substring(0, Math.min(3, n.length())), k -> new ArrayList<>()).add(r);
        }
        List<Finding> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<Row> block : blocks.values()) {
            if (block.size() > DUPLICATE_BLOCK_LIMIT) block = block.subList(0, DUPLICATE_BLOCK_LIMIT);
            for (int i = 0; i < block.size(); i++) {
                for (int j = i + 1; j < block.size(); j++) {
                    Row a = block.get(i), b = block.get(j);
                    String na = normalise(a.name()), nb = normalise(b.name());
                    boolean same = na.equals(nb) || na.replace(" ", "").equals(nb.replace(" ", ""));
                    if (!same && jaroWinkler(na, nb) < SIMILARITY) continue;
                    if (!compatibleSizes(na, nb)) continue;
                    if (seen.add(a.id() + ":" + b.id())) {
                        out.add(new Finding(Kind.POSSIBLE_DUPLICATE, a.id(), "Looks like '" + b.name() + "'" + (b.sku() != null ? " (" + b.sku() + ")" : "") + "."));
                        out.add(new Finding(Kind.POSSIBLE_DUPLICATE, b.id(), "Looks like '" + a.name() + "'" + (a.sku() != null ? " (" + a.sku() + ")" : "") + "."));
                    }
                }
            }
        }
        return out;
    }

    /** "Cement 25 kg" and "Cement 50 kg" differ by a number, so they are different products, however alike they read. */
    static boolean compatibleSizes(String a, String b) {
        return digits(a).equals(digits(b));
    }

    private static List<String> digits(String s) {
        List<String> out = new ArrayList<>();
        var m = java.util.regex.Pattern.compile("\\d+").matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    /** Jaro-Winkler similarity, 0 to 1. */
    static double jaroWinkler(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int l1 = s1.length(), l2 = s2.length();
        if (l1 == 0 || l2 == 0) return 0.0;
        int range = Math.max(0, Math.max(l1, l2) / 2 - 1);
        boolean[] m1 = new boolean[l1], m2 = new boolean[l2];
        int matches = 0;
        for (int i = 0; i < l1; i++) {
            for (int j = Math.max(0, i - range); j < Math.min(l2, i + range + 1); j++) {
                if (!m2[j] && s1.charAt(i) == s2.charAt(j)) { m1[i] = m2[j] = true; matches++; break; }
            }
        }
        if (matches == 0) return 0.0;
        int t = 0, k = 0;
        for (int i = 0; i < l1; i++) {
            if (!m1[i]) continue;
            while (!m2[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) t++;
            k++;
        }
        double jaro = ((double) matches / l1 + (double) matches / l2 + (matches - t / 2.0) / matches) / 3.0;
        int prefix = 0;
        while (prefix < Math.min(4, Math.min(l1, l2)) && s1.charAt(prefix) == s2.charAt(prefix)) prefix++;
        return jaro + prefix * 0.1 * (1 - jaro);
    }
}
