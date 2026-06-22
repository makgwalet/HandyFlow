package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OutstandingBalanceResponse(
        UUID       patientId,
        String     patientName,
        String     phone,
        BigDecimal totalBilled,
        BigDecimal totalPaid,
        BigDecimal balance,
        Instant    oldestUnpaid,
        int        claimCount
) {}
