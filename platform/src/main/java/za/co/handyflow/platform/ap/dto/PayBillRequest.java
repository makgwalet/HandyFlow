package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code amount} is optional: left empty, the whole balance is paid. A smaller amount is a part payment (finance patch F10). */
public record PayBillRequest(
        String     paymentRef,
        UUID       bankAccountId,
        BigDecimal amount
) {
    public PayBillRequest(String paymentRef, UUID bankAccountId) { this(paymentRef, bankAccountId, null); }
}
