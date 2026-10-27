package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VendorInvoiceResponse(
        UUID id, UUID purchaseOrderId, String poNumber, UUID vendorId, String vendorName, String invoiceNumber,
        LocalDate invoiceDate, BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total, String status,
        UUID recordedByUserId, UUID decidedByUserId, Instant decidedAt, String decisionNote, Instant createdAt
) {}
