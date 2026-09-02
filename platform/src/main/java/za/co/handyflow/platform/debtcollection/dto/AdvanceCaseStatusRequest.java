package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;

/** newStatus == CLOSED is rejected by DebtCollectionCase.advanceStatus() itself — use /close instead. */
public record AdvanceCaseStatusRequest(@NotNull CaseStatus status) {}
