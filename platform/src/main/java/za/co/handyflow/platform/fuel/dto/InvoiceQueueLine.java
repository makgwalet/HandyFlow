package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One delivered load waiting for an invoice. */
public record InvoiceQueueLine(
        UUID deliveryId, String receiptNumber, UUID customerId, String customerName, String fuelType,
        BigDecimal litresDelivered, BigDecimal pricePerLitre, BigDecimal totalAmount, Instant deliveredAt,
        String invoiceState, String invoiceError
) {}
