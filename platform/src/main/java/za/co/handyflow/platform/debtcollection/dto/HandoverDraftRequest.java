package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.CreditAgreementType;

import java.util.Set;
import java.util.UUID;

/** Create or update a DRAFT handover. Changing agreementType resets any recorded prescription review. */
public record HandoverDraftRequest(
        CreditAgreementType agreementType,
        String creditorAuthorityNote,
        Set<UUID> evidenceIds,
        String notes
) {}
