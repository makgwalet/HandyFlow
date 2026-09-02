package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationStatus;

/** finalStatus is validated as terminal (SETTLED/WITHDRAWN/CLOSED) by LitigationMatter.close() itself — the entity, not this DTO, is the source of truth for that rule. */
public record CloseLitigationMatterRequest(@NotNull LitigationStatus finalStatus, String outcomeNotes) {}
