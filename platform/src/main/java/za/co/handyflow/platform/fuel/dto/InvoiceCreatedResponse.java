package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceCreatedResponse(UUID invoiceId, String invoiceNumber, BigDecimal subtotal, BigDecimal vatTotal, BigDecimal total, int deliveries) {}
