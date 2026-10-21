package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * How complete an item's basic record is, as a score out of 100. Goods (products, consumables) are also expected to have
 * a barcode and brand; services and other offerings are not. The dashboard's "incomplete records" count uses the same
 * checks (see CatalogueItemRepository.countIncomplete), so the two always agree. No Spring, no database.
 */
public final class CompletenessRules {
    private CompletenessRules() {}

    /** What the checks look at. */
    public record Facts(CatalogueItemType type, String description, boolean hasCategory, String sku, String barcode, String brand, BigDecimal price) {}

    public record Check(String code, String label, boolean passed, int weight) {}

    public record Result(int score, List<Check> checks) {
        public List<Check> failed() { return checks.stream().filter(c -> !c.passed()).toList(); }
    }

    public static boolean isGoods(CatalogueItemType type) { return type == CatalogueItemType.PRODUCT || type == CatalogueItemType.CONSUMABLE; }

    private static boolean has(String s) { return s != null && !s.isBlank(); }

    public static Result evaluate(Facts f) {
        var checks = new java.util.ArrayList<Check>();
        checks.add(new Check("description", "Description", has(f.description()), 20));
        checks.add(new Check("category", "Category", f.hasCategory(), 15));
        checks.add(new Check("sku", "SKU", has(f.sku()), 20));
        checks.add(new Check("price", "Selling price", f.price() != null && f.price().signum() > 0, 20));
        if (isGoods(f.type())) {
            checks.add(new Check("barcode", "Barcode", has(f.barcode()), 15));
            checks.add(new Check("brand", "Brand", has(f.brand()), 10));
        }
        int total = checks.stream().mapToInt(Check::weight).sum();
        int got = checks.stream().filter(Check::passed).mapToInt(Check::weight).sum();
        return new Result((int) Math.round(100.0 * got / total), List.copyOf(checks));
    }
}
