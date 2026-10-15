package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** The draft that was created. It has not been issued: review and issue it in Invoicing. */
public record DraftInvoiceResponse(UUID invoiceId, String invoiceNumber, BigDecimal subtotal, BigDecimal vatTotal, BigDecimal total, int charges) {}
