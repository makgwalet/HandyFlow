package za.co.handyflow.platform.clinic.dto.billing;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BatchSubmitClaimsRequest(
        @NotEmpty(message = "At least one claim ID is required")
        List<UUID> claimIds
) {}