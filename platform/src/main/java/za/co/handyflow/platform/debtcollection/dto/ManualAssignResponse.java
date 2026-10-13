package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.application.internal.AllocationService.ManualResult;

import java.util.List;
import java.util.UUID;

public record ManualAssignResponse(UUID caseId, String caseNumber, UUID assignedToUserId, String assignedToUserName,
                                   List<String> warnings, boolean overridden) {
    public static ManualAssignResponse of(ManualResult r) {
        return new ManualAssignResponse(r.debtCase().getId(), r.debtCase().getCaseNumber(), r.debtCase().getAssignedToUserId(),
                r.debtCase().getAssignedToUserName(), r.warnings(), r.overridden());
    }
}
