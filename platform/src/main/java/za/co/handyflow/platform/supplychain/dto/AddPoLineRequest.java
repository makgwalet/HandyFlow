package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.util.UUID;

// Request DTO for POST /purchase-orders/{id}/lines
// Maps to ScPoLine.create() — note ScPoLine uses itemName not description
public record AddPoLineRequest(
        String     itemName,        // required — description of the line item
        String     supplierSku,     // optional — supplier's own part/SKU code
        BigDecimal qtyOrdered,      // required
        BigDecimal unitCost,        // required
        BigDecimal vatRate,         // optional — defaults to 15% if null
        UUID       catalogueItemId  // optional — links to HandyFlow catalogue
) {}
