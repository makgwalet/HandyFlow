package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.ClosureReason;

public record CloseCaseRequest(@NotNull ClosureReason reason, String outcomeNotes) {}
