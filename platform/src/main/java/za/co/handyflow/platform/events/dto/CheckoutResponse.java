package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CheckoutResponse(
        UUID paymentId,
        UUID guestId,
        String ticketNumber,        // reserved now, but NOT valid for entry until the payment is confirmed
        BigDecimal amount,
        String currency,
        String provider,
        Instant expiresAt,          // the seat is released if payment hasn't arrived by then
        String redirectUrl,         // where to send the payer, or null for offline payment
        String instructions
) {}