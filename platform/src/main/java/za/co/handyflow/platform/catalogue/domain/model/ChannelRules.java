package za.co.handyflow.platform.catalogue.domain.model;

import java.util.*;
import java.util.regex.Pattern;

/** Rules for channels and for what an item needs before it may be published to one. Pure, no database. */
public final class ChannelRules {

    private ChannelRules() {}

    public static final int MAX_CHANNELS = 20;
    public static final int MAX_ITEMS_PER_CALL = 200;
    private static final Pattern CODE = Pattern.compile("^[a-z0-9][a-z0-9-]{1,29}$");

    /** Lower-case letters, digits and hyphens, 2 to 30 characters. Used in the feed address, so it must be tidy. */
    public static String code(String raw) {
        String c = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(c).matches()) throw new IllegalArgumentException("The code must be 2 to 30 letters, digits or hyphens, for example web-shop.");
        return c;
    }

    public static String name(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("A channel needs a name.");
        if (n.length() > 80) throw new IllegalArgumentException("The name can be at most 80 characters.");
        return n;
    }

    public static ChannelKind kind(String raw) {
        try { return ChannelKind.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose what kind of channel this is: till, web, marketplace, print or other."); }
    }

    /** What the rules need to know about an item. */
    public record Facts(CatalogueItemStatus status, boolean variant, int activeVariants, java.math.BigDecimal price, String name,
                        String description, String sku, String barcode, boolean hasPicture) {}

    /**
     * What stops an item from being published to this kind of channel, in plain words; empty when it can go.
     * Variants are never published on their own (they go with their main item), and an item sold through variants is
     * judged by its variants, not by its own price.
     */
    public static List<String> problems(ChannelKind kind, Facts f) {
        List<String> out = new ArrayList<>();
        if (f.variant()) { out.add("Publish the main item. Its variants go with it."); return out; }
        if (f.status() != CatalogueItemStatus.ACTIVE) out.add("Only active items can be published.");
        boolean hasVariants = f.activeVariants() > 0;
        if (!hasVariants && (f.price() == null || f.price().signum() <= 0)) out.add("It has no selling price on this channel's price list.");
        boolean shop = kind == ChannelKind.WEB || kind == ChannelKind.MARKETPLACE;
        if (shop) {
            if (blank(f.description())) out.add("It needs a description.");
            if (!f.hasPicture()) out.add("It needs a picture.");
            if (blank(f.sku())) out.add("It needs a SKU.");
        }
        if (kind == ChannelKind.POS && !hasVariants && blank(f.sku()) && blank(f.barcode())) out.add("It needs a SKU or a barcode so it can be found at the till.");
        return out;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    // ── Change feed ─────────────────────────────────────────────────────────

    public static final int DEFAULT_FEED_LIMIT = 200;
    public static final int MAX_FEED_LIMIT = 500;

    public static long cursor(Long since) {
        if (since != null && since < 0) throw new IllegalArgumentException("The cursor cannot be negative.");
        return since == null ? 0L : since;
    }

    public static int feedLimit(Integer limit) {
        if (limit == null) return DEFAULT_FEED_LIMIT;
        if (limit < 1) throw new IllegalArgumentException("The limit must be at least 1.");
        return Math.min(limit, MAX_FEED_LIMIT);
    }
}
