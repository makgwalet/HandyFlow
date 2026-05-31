package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CreateContractRequest(
        @NotBlank String title,
        @NotBlank String contractType,
        UUID templateId,
        String body,
        Map<String, String> variables,   // ← add this
        BigDecimal valueAmount,
        LocalDate startDate,
        LocalDate endDate,
        boolean autoRenew,
        Integer renewalNoticeDays,
        String notes
) {}