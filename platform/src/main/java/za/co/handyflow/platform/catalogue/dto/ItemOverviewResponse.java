package za.co.handyflow.platform.catalogue.dto;

import java.util.List;

/** The Product 360 header and overview: the item, how complete it is, and what is outstanding. */
public record ItemOverviewResponse(CatalogueItemResponse item, String categoryPath, Completeness completeness, List<String> missingAttributes,
                                   long openPriceChanges, String approvalState, String parentName) {

    public record Completeness(int score, List<CheckLine> checks) {}

    public record CheckLine(String code, String label, boolean passed) {}
}
