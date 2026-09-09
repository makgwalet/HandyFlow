// fuel/dto/CompleteDeliveryRequest.java

package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CompleteDeliveryRequest(
        @NotNull BigDecimal litresDelivered,
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
        String receiverSignatureUrl
) {}