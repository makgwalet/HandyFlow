package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record RecordClientTenderOutcomeRequest(@NotBlank String outcome, String reason, BigDecimal awardedValue) {}
