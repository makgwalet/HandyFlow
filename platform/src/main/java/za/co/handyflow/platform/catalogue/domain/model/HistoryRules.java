package za.co.handyflow.platform.catalogue.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * What a reader may see of a history line. Cost prices, the minimum price and the workflow around other lists' prices are
 * only for people who may see prices (CATALOGUE_PRICE_READ); everyone else gets the line without those figures.
 */
public final class HistoryRules {
    private HistoryRules() {}

    public static String redact(CatalogueAuditAction action, String detail, boolean canSeePrices) {
        if (detail == null || canSeePrices) return detail;
        switch (action) {
            case PRICE_PROPOSED, PRICE_APPROVED, PRICE_REJECTED, PRICE_CANCELLED -> { return null; }
            default -> { }
        }
        List<String> kept = new ArrayList<>();
        for (String part : detail.split("; ")) {
            String p = part.trim();
            if (p.startsWith("cost:") || p.startsWith("minimum price:")) continue;
            // A price change made through the pricing workflow reads "Retail: 100.00 -> 120.00 ..."; only the item form's own "price:" is the plain selling price.
            if (action == CatalogueAuditAction.PRICE_CHANGED && !p.startsWith("price:")) continue;
            kept.add(p);
        }
        return kept.isEmpty() ? null : String.join("; ", kept);
    }
}
