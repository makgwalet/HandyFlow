package za.co.handyflow.platform.catalogue.dto;

import za.co.handyflow.platform.catalogue.domain.model.DashboardRules.CategoryCount;
import za.co.handyflow.platform.catalogue.domain.model.DashboardRules.MonthPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything the Catalogue dashboard shows. {@code prices} is null for users who may not see or decide prices. */
public record DashboardResponse(Counts counts, PriceQueue prices, List<MonthPoint> growth, List<CategoryCount> byCategory,
                                List<RecentItem> recentItems, List<ActivityLine> activity) {

    /** Items and products/services are top-level and not archived; variants are counted on their own. */
    public record Counts(long items, long products, long services, long variants, long activeSkus, long drafts, long suspended,
                         long discontinued, long discontinuedRecently, long incomplete) {}

    public record PriceQueue(long pending, long scheduled) {}

    public record RecentItem(UUID id, String name, String sku, String categoryName, String status, BigDecimal defaultPrice, Instant updatedAt, UUID parentId) {}

    public record ActivityLine(String action, String detail, String entityType, UUID entityId, String entityName, Instant at) {}
}
