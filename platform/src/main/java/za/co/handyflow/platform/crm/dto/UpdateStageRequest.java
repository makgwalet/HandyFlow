package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.model.LostReason;

/** lostReason is required when stage is LOST and ignored otherwise; lostNote is required when the reason is OTHER. */
public record UpdateStageRequest(
        @NotNull LeadStage stage,
        LostReason lostReason,
        @Size(max = 500) String lostNote
) {}
