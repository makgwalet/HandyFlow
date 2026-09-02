package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Links a case to a contracting.Contract (typically an Acknowledgment of Debt) by id only — see package-info for why this module has no ContractingFacade dependency. */
public record LinkContractRequest(@NotNull UUID contractId) {}
