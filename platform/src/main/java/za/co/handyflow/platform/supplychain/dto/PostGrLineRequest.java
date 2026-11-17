package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One delivered line.
 * poLineId  — preferred link to the PO line (required unless catalogueItemId resolves to one on the PO).
 * qtyReceived — what physically arrived (including anything you reject).
 * qtyRejected — optional; refused at inspection. Accepted = received - rejected goes to stock.
 *               A rejectionReason is required when qtyRejected > 0.
 * unitCost    — optional; defaults to the PO line's unit cost.
 * condition   — optional; defaults to GOOD, or DAMAGED when anything was rejected.
 * Existing clients that send only catalogueItemId/itemName/qtyReceived/unitCost/lotNumber/
 * expiryDate keep working.
 */
public record PostGrLineRequest(
        UUID poLineId,
        UUID catalogueItemId,
        String itemName,
        BigDecimal qtyReceived,
        BigDecimal qtyRejected,
        String rejectionReason,
        String condition,
        BigDecimal unitCost,
        String lotNumber,
        LocalDate expiryDate
) {}
