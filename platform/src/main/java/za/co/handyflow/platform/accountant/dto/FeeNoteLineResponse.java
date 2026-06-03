package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FeeNoteLineResponse(
        UUID id,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        BigDecimal amount
) {
}
