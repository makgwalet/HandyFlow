package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A package and its parts. Money fields are null for people who may not see prices. {@code saving} is what the customer
 * saves against buying the parts separately (negative when the package costs more); null when any price is missing.
 */
public record BundleResponse(UUID bundleId, String name, String sku, String type, BigDecimal price, List<Line> components,
                             BigDecimal partsTotal, BigDecimal partsCost, BigDecimal saving, int unpricedParts) {

    public record Line(UUID itemId, String name, String sku, String status, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
