package za.co.handyflow.platform.approvals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * conditions/approverChain are raw JSON strings, not typed objects —
 * matches ApprovalRule's own JSONB storage exactly (see that entity's
 * Javadoc for why a common typed structure would defeat the point of a
 * rule table any module can use unmodified). Validation of well-formed
 * JSON and of the shape the engine expects happens in
 * ApprovalEngineService, not here — a tenant admin submitting malformed
 * rule JSON should get a clear error from the service layer, not a
 * silently-accepted bad row.
 */
public record ApprovalRuleRequest(
        @NotBlank String module,
        @NotBlank String entityType,
        @NotBlank String name,
        int priority,
        String conditions,
        @NotNull String approvalMode,   // "SEQUENTIAL" | "PARALLEL_ALL" | "PARALLEL_ANY_ONE"
        @NotBlank String approverChain,
        boolean active
) {}