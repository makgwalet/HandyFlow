package za.co.handyflow.platform.security.dto;

import java.util.List;
import java.util.UUID;

// The guard-facing "My Post" screen's single combined read: the
// site-level order (general rules everyone at this site needs) plus
// every post at this site the guard can pick to view that post's own
// order -- no formal "guard is assigned to post X" concept exists yet
// in this codebase, so the guard app lets the guard pick their own
// post from this list rather than the backend guessing one.
// needsAcknowledgment on each order is resolved server-side per the
// calling guard, not left for the client to compute from a version
// number comparison it might get wrong.
public record MyPostResponse(
        UUID siteId,
        PostOrderResponse siteLevelOrder,   // null if none has ever been published for this site
        boolean siteLevelNeedsAcknowledgment,
        List<PostSummary> posts
) {
    public record PostSummary(UUID postId, String postName, boolean hasActiveOrder) {}
}
