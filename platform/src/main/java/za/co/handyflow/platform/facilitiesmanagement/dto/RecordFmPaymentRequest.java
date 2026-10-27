package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Only {@code amount} is required (existing clients keep working). paymentDate defaults to today and cannot be in the future; method: EFT, CASH, CARD, CHEQUE or OTHER. */
public record RecordFmPaymentRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount, LocalDate paymentDate, String method, String reference,
        /** Optional (the Idempotency-Key header does the same). Sending the same key again returns the first result instead of recording twice. */
        @Size(max = 100) String idempotencyKey
) {
    public RecordFmPaymentRequest withIdempotencyKey(String key) {
        return new RecordFmPaymentRequest(amount, paymentDate, method, reference, key);
    }
}