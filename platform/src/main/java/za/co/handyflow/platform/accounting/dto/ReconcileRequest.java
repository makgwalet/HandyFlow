package za.co.handyflow.platform.accounting.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReconcileRequest(
        @NotNull UUID journalLineId
) {}