package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.crm.domain.model.LostReason;
import za.co.handyflow.platform.crm.domain.model.OpportunityStage;

public record OpportunityStageRequest(
        @NotNull OpportunityStage stage,
        LostReason lostReason,
        @Size(max = 500) String lostNote
) {}
