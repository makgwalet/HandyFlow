package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Supplier returns and claims. */
public final class ReturnDtos {

    private ReturnDtos() {}

    /** One line. Against a receipt line: grLineId (item, cost and PO line are taken from it). Otherwise itemName, qty and unitCost. */
    public record LineRequest(UUID grLineId, String itemName, BigDecimal qty, BigDecimal unitCost) {}

    public record CreateRequest(String kind, String reason, UUID supplierId, UUID goodsReceiptId, String notes, List<LineRequest> lines) {}

    public record DecisionRequest(String note) {}

    /** creditAmount is the credit note's total including VAT; creditVat (optional, default 0) is the VAT inside it. */
    public record CreditRequest(String creditNoteRef, BigDecimal creditAmount, BigDecimal creditVat) {
        public CreditRequest(String creditNoteRef, BigDecimal creditAmount) { this(creditNoteRef, creditAmount, null); }
    }

    public record LineResponse(UUID id, UUID grLineId, UUID poLineId, UUID catalogueItemId, String itemName, BigDecimal qty,
                               BigDecimal unitCost, BigDecimal lineTotal) {}

    public record Response(UUID id, String returnNumber, String kind, String reason, String status, UUID supplierId, String supplierName,
                           UUID purchaseOrderId, UUID goodsReceiptId, UUID inspectionId, BigDecimal expectedCredit, String creditNoteRef,
                           BigDecimal creditAmount, String notes, String statusReason, String createdByName, String approvedByName,
                           Instant createdAt, Instant approvedAt, Instant dispatchedAt, Instant creditedAt, List<LineResponse> lines) {}
}
