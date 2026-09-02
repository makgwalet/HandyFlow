package za.co.handyflow.platform.legalcompliance.dto;

import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatter;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatterType;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LitigationMatterResponse(
        UUID id,
        String matterNumber,
        String title,
        LitigationMatterType matterType,
        LitigationStatus status,
        String opposingParty,
        String ourSide,
        BigDecimal estimatedExposure,
        String legalRepresentative,
        String courtOrForum,
        String caseReference,
        LocalDate openedDate,
        LocalDate nextKeyDate,
        LocalDate closedDate,
        String description,
        String outcomeNotes,
        UUID linkedContractId,
        Instant createdAt,
        Instant updatedAt
) {
    public static LitigationMatterResponse of(LitigationMatter m) {
        return new LitigationMatterResponse(
                m.getId(), m.getMatterNumber(), m.getTitle(), m.getMatterType(), m.getStatus(),
                m.getOpposingParty(), m.getOurSide(), m.getEstimatedExposure(), m.getLegalRepresentative(),
                m.getCourtOrForum(), m.getCaseReference(), m.getOpenedDate(), m.getNextKeyDate(),
                m.getClosedDate(), m.getDescription(), m.getOutcomeNotes(), m.getLinkedContractId(),
                m.getCreatedAt(), m.getUpdatedAt());
    }
}
