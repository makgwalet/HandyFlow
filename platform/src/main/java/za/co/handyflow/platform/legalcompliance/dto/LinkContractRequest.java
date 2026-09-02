package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Shared by RegulatoryObligationController and LitigationMatterController — both link to a contracting.Contract via ContractingFacade, never directly. */
public record LinkContractRequest(@NotNull UUID contractId) {}
