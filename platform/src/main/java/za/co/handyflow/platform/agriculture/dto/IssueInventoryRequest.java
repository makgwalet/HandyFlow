package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record IssueInventoryRequest(
        @NotNull BigDecimal quantity,
        UUID performedBy,
        String referenceType,
        UUID referenceId,
        String notes
) {}
