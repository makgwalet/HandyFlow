package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Supplier contracts. */
public final class ContractDtos {

    private ContractDtos() {}

    public record LineRequest(UUID catalogueItemId, String description, BigDecimal agreedUnitPrice, BigDecimal minQty) {}

    public record SaveRequest(UUID supplierId, String title, LocalDate startDate, LocalDate endDate, String currency, BigDecimal valueCap,
                              Integer paymentTermsDays, Boolean autoRenew, Integer renewalNoticeDays, UUID documentId, String notes,
                              List<LineRequest> lines) {}

    public record ReasonRequest(String reason) {}

    public record LineResponse(UUID id, UUID catalogueItemId, String description, BigDecimal agreedUnitPrice, BigDecimal minQty) {}

    public record Response(UUID id, String contractNumber, UUID supplierId, String supplierName, String title, String status, LocalDate startDate,
                           LocalDate endDate, String currency, BigDecimal valueCap, Integer paymentTermsDays, boolean autoRenew, int renewalNoticeDays,
                           UUID documentId, String notes, String terminatedReason, String createdByName, String activatedByName, Instant activatedAt,
                           boolean renewalNoticeDue, List<LineResponse> lines) {}

    /** A PO line priced above the agreed contract price. */
    public record PriceVariance(UUID poLineId, String itemName, BigDecimal poUnitCost, BigDecimal agreedUnitPrice, BigDecimal overPct, String contractNumber) {}
}
