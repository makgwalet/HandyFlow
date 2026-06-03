package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FeeNoteResponse(
        UUID id,
        UUID clientId,
        String clientName,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal balance,
        String status,
        int daysOverdue,
        List<FeeNoteLineResponse> lines,
        Instant createdAt
) {
}
