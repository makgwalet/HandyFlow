package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Deliberately carries the ticket number but no guest name or email — finance doesn't need them to reconcile. */
public record PaymentResponse(
        UUID id,
        UUID guestId,
        String ticketNumber,
        BigDecimal amount,              // price asked for
        BigDecimal receivedAmount,      // what actually arrived; null until money is received
        BigDecimal feeAmount,
        BigDecimal refundedAmount,
        BigDecimal refundableAmount,    // received - refunded, while we hold the money
        String currency,
        String provider,
        String providerRef,
        String status,                  // PENDING | PAID | FAILED | EXPIRED | CANCELLED | REQUIRES_REFUND
        Instant expiresAt,
        Instant paidAt,
        String failureReason,
        String reviewReason,            // why a payment is REQUIRES_REFUND
        Instant createdAt
) {}