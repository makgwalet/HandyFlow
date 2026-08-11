package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.crm.domain.model.LeadStage;

public record UpdateStageRequest(@NotNull LeadStage stage) {}