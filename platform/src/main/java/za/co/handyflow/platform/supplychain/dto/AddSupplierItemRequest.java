package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for POST /api/v1/supply-chain/suppliers/{id}/items
 *
 * Adds an item to a supplier's pricing catalogue (sc_supplier_items table).
 * Once added, the item appears in best-price lookups when buyers create PO lines.
 *
 * catalogueItemId  — link to the HandyFlow catalogue (nullable for bespoke items)
 * itemName         — the supplier's own name for this item (required)
 * supplierSku      — the supplier's part/SKU code (optional)
 * unitCost         — the agreed price per unit (required)
 * leadTimeDays     — how many days from order to delivery (defaults to 7 if null)
 * minOrderQty      — minimum quantity the supplier will accept (defaults to 1 if null)
 * isPreferred      — mark this as the preferred supplier for this item
 */
public record AddSupplierItemRequest(
        UUID       catalogueItemId,
        String     itemName,
        String     supplierSku,
        BigDecimal unitCost,
        Integer    leadTimeDays,
        BigDecimal minOrderQty,
        Boolean    isPreferred
) {}