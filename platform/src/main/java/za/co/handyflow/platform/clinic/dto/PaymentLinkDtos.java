package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;

/** Linking a patient's payments to the visits they pay for (patch 0190). */
public final class PaymentLinkDtos {
    private PaymentLinkDtos() {}

    /** @param linked how much was linked to visits; @param onAccount how much of the payment(s) is still not linked to a visit */
    public record LinkResult(BigDecimal linked, BigDecimal onAccount, int visitsPaidOff) {}
}
