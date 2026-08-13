package za.co.handyflow.platform.recruitmentagency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RequisitionResponse(
        UUID id, UUID clientId, String clientName, String requisitionNumber,
        String title, String description, BigDecimal salaryMin, BigDecimal salaryMax,
        String location, String employmentType, String status,
        LocalDate targetStartDate, String notes, int candidateCount, Instant createdAt
) {}