package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Supplier invoice capture and its three-way match. */
public final class SupplierInvoiceDtos {
    private SupplierInvoiceDtos() {}

    public record CaptureRequest(@NotNull UUID supplierId, UUID purchaseOrderId, @NotBlank @Size(max = 60) String invoiceNumber,
                                 @NotNull LocalDate invoiceDate, LocalDate dueDate,
                                 @NotNull @Positive BigDecimal litres, @NotNull @PositiveOrZero @DecimalMax("1000") BigDecimal pricePerLitre,
                                 @PositiveOrZero BigDecimal otherCharges, @PositiveOrZero BigDecimal vatAmount,
                                 @Size(max = 500) String notes, List<UUID> receiptIds) {}

    public record DecisionRequest(@Size(max = 500) String reason) {}

    public record ReceiptLine(UUID receiptId, Instant receivedAt, BigDecimal litres, BigDecimal pricePerLitre, BigDecimal totalCost,
                              String deliveryNote, boolean reversed) {}

    public record InvoiceResponse(UUID id, UUID supplierId, String supplierName, UUID purchaseOrderId, String poNumber, String invoiceNumber,
                                  LocalDate invoiceDate, LocalDate dueDate, BigDecimal litres, BigDecimal pricePerLitre, BigDecimal otherCharges,
                                  BigDecimal vatAmount, BigDecimal totalAmount, String status, String matchStatus, String decisionReason, String notes,
                                  Instant capturedAt, Instant decidedAt, BigDecimal litresReceived, BigDecimal receiptValue, BigDecimal litresVariance,
                                  BigDecimal goodsValue, BigDecimal valueVariance, BigDecimal priceVsPo, List<String> issues, List<ReceiptLine> receipts,
                                  UUID apBillId, Instant apHandedOffAt) {}
}
