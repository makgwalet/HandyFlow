package za.co.handyflow.platform.security.dto;

import java.util.List;
import java.util.UUID;

// postId null -> site-level draft. Starting from the currently-ACTIVE
// version's own content is the caller's job (fetch it, prefill a form,
// submit the edited version here) -- this request is just the new
// draft's own content, not a diff.
public record CreatePostOrderDraftRequest(
        UUID postId,
        String instructions,
        String duties,
        String emergencyProcedures,
        String restrictedAreas,
        String accessRules,
        List<UUID> contactIds
) {}
