package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationStatus;

public record AdvanceLitigationStatusRequest(@NotNull LitigationStatus status) {}
