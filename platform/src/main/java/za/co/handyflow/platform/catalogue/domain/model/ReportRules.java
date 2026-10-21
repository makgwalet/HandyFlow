package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The catalogue reports on offer, and the small calculations behind them. The roadmap named twelve reports without
 * listing them, so these twelve were chosen from what a catalogue owner asks for (see the audit, section 16).
 * No Spring, no database.
 */
public final class ReportRules {

    private ReportRules() {}

    public static final int MAX_ROWS = 5000;
    public static final int DEFAULT_LOW_MARGIN = 20;
    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 730;

    /** A report in the list. {@code needsPrices} reports are shown only to people who may see prices and costs. */
    public record Def(String code, String group, String title, String description, boolean needsPrices) {}

    public static final List<Def> ALL = List.of(
            new Def("PRICE_LIST", "Pricing", "Price list", "Every live item with its price on one price list, with and without VAT.", false),
            new Def("MARGIN", "Pricing", "Margins", "Price, cost and margin of every active item, lowest margin first.", true),
            new Def("LOW_MARGIN", "Pricing", "Low margins", "Active items whose margin is below a limit (default 20%).", true),
            new Def("NO_COST", "Pricing", "Items without a cost", "Active goods and services with a price but no cost, so no margin can be worked out.", true),
            new Def("PRICE_CHANGES", "Pricing", "Price changes", "Price changes that took effect in the last days (default 90).", true),
            new Def("PACKAGES", "Pricing", "Packages and savings", "Each package, what its parts are worth, its price and the saving.", true),
            new Def("BY_CATEGORY", "Catalogue", "Items by category", "How many items each category holds and their average price.", false),
            new Def("BY_STATUS", "Catalogue", "Items by status", "How many items are draft, active, discontinued or archived.", false),
            new Def("RECENT_CHANGES", "Catalogue", "Recently changed items", "Items changed in the last days (default 30), newest first.", false),
            new Def("NO_PICTURE", "Gaps", "Items without a picture", "Main items (not variants) that have no picture.", false),
            new Def("NO_BARCODE", "Gaps", "Goods without a barcode", "Products and consumables that have no barcode.", false),
            new Def("NOT_PUBLISHED", "Gaps", "Active items not on any channel", "Active items that are not published to any channel.", false));

    public static Optional<Def> find(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        return ALL.stream().filter(d -> d.code().equals(c)).findFirst();
    }

    /** Margin as a share of the selling price, like the item screens: (price - cost) / price. Null when it cannot be worked out. */
    public static BigDecimal margin(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null || price.signum() <= 0) return null;
        return price.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal lowMarginLimit(Integer limit) {
        int l = limit == null ? DEFAULT_LOW_MARGIN : limit;
        if (l < 0 || l > 99) throw new IllegalArgumentException("The margin limit must be between 0 and 99 percent.");
        return BigDecimal.valueOf(l);
    }

    public static int days(Integer days, int fallback) {
        int d = days == null ? fallback : days;
        if (d < 1 || d > MAX_DAYS) throw new IllegalArgumentException("Days must be between 1 and " + MAX_DAYS + ".");
        return d;
    }

    public static String money(BigDecimal v) { return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
}
