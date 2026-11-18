// fuel/dto/CompleteDeliveryRequest.java

package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.Positive;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CompleteDeliveryRequest(
        @NotNull @Positive(message = "Litres delivered must be greater than zero") BigDecimal litresDelivered,
        String receiverName,        // person at mine who accepted delivery
        String receiverIdBadge,     // their ID or site badge number
        BigDecimal meterReadingStart, // pump meter at start
        BigDecimal meterReadingEnd,    // pump meter at end — confirms litres
        // WHY? Captures when designated receiver is absent
        // Driver records who actually signed and on whose behalf
        Boolean signedOnBehalf,
        String onBehalfOf,          // name of the designated receiver being represented
        // FIX (P0 backlog item 1.9): receiverSignatureUrl existed on the
        // entity but nothing ever populated it — the PDF's "Signature"
        // line was permanently a static placeholder regardless of what
        // actually happened at delivery. Optional data: URI, same
        // convention as tenant logoUrl elsewhere in this codebase (see
        // ReceiptPdfService.decodeLogoBytes) — no new storage
        // infrastructure needed, just actually wiring the field that
        // was already there.
        String receiverSignatureUrl,
        // Stage 3: generated once on the device. Sending the same id again returns the first result instead of failing or repeating.
        java.util.UUID clientRequestId,
        // Batch B: set only when nobody can receive the fuel. One of UNATTENDED_SITE, AFTER_HOURS_APPROVED, AUTOMATED_RECEIVING,
        // CONTACTLESS_AUTHORISED. The server checks it against the company's policy, the site and the evidence on the load.
        String podExceptionType,
        String podExceptionReason
) {
    /** The shape before exceptions existed, so existing callers and tests keep working. */
    public CompleteDeliveryRequest(BigDecimal litresDelivered, String receiverName, String receiverIdBadge, BigDecimal meterReadingStart,
                                   BigDecimal meterReadingEnd, Boolean signedOnBehalf, String onBehalfOf, String receiverSignatureUrl,
                                   java.util.UUID clientRequestId) {
        this(litresDelivered, receiverName, receiverIdBadge, meterReadingStart, meterReadingEnd, signedOnBehalf, onBehalfOf,
                receiverSignatureUrl, clientRequestId, null, null);
    }
}