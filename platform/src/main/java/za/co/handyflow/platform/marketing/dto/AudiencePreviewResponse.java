package za.co.handyflow.platform.marketing.dto;

import java.util.List;

/**
 * matched     = contacts matching the rules (any consent state)
 * notOptedIn  = matched but never opted in (will not receive anything)
 * suppressed  = opted in but on the suppression list (blocked)
 * eligible    = what a campaign would actually be queued for
 */
public record AudiencePreviewResponse(
        long matched,
        long notOptedIn,
        long suppressed,
        long eligible,
        List<SampleContact> sample
) {
    public record SampleContact(String name, String email) {}
}