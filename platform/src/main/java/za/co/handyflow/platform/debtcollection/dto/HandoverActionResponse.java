package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.application.internal.HandoverReadinessChecker.Finding;
import za.co.handyflow.platform.debtcollection.application.internal.LegalHandoverService.ActionResult;

import java.util.List;

/** done=false means a BLOCKING finding stopped the step (nothing changed); findings explain what to fix. */
public record HandoverActionResponse(HandoverResponse handover, List<Finding> findings, boolean done) {
    public static HandoverActionResponse of(ActionResult r) {
        return new HandoverActionResponse(HandoverResponse.of(r.handover()), r.findings(), r.done());
    }
}
