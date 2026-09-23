package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record RecordTenderOutcomeRequest(@NotBlank String outcome, String reason, BigDecimal awardedValue) {}
