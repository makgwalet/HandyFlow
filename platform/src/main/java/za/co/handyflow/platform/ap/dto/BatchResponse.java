package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BatchResponse(
        UUID               id,
        String             batchNumber,
        UUID               bankAccountId,
        String             bankAccountName,
        String             description,
        BigDecimal         totalAmount,
        int                billCount,
        String             status,
        LocalDate          paymentDate,
        String             paymentRef,
        boolean            hasPop,
        List<BillResponse> bills,
        Instant            submittedAt,
        Instant            paidAt,
        Instant            createdAt
) {}
