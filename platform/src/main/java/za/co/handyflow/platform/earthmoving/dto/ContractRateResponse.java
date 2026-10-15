package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The rates held on a deployment's hire contract. {@code contractId} is null when no contract is linked yet. */
public record ContractRateResponse(UUID deploymentId, UUID contractId, String contractNumber, List<Rate> rates) {
    public record Rate(String basis, BigDecimal unitRate, BigDecimal includedKm, Instant updatedAt) {}
}
