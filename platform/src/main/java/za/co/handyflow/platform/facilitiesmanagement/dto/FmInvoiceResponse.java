package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** STAFF-facing response. Never return this to portal users — see {@link FmPortalInvoiceResponse}. */
public record FmInvoiceResponse(
        UUID id, UUID clientId, String invoiceNumber, LocalDate periodStart, LocalDate periodEnd,
        LocalDate issueDate, LocalDate dueDate, BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total,
        BigDecimal amountPaid, BigDecimal balance, String status, Instant createdAt,
        BigDecimal vatRate, UUID preparedByUserId, UUID approvedByUserId, Instant approvedAt,
        String glStatus, String glLastError, UUID agreementId,
        BigDecimal creditedAmount, boolean taxInvoice,
        String supplierName, String supplierVatNumber, String supplierAddress,
        String clientName, String clientVatNumber, String clientAddress
) {}