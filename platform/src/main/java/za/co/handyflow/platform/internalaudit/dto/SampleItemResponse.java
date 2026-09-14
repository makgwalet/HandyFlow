package za.co.handyflow.platform.internalaudit.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SampleItemResponse(
        UUID id, UUID journalEntryId, String entryNumberSnapshot, LocalDate entryDateSnapshot,
        BigDecimal amountSnapshot, String notes, Instant selectedAt,
        List<AuditTestResponse> tests
) {}
