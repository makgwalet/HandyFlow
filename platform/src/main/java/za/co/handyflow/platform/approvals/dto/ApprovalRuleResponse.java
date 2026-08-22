package za.co.handyflow.platform.approvals.dto;

import java.time.Instant;
import java.util.UUID;

public record ApprovalRuleResponse(
        UUID id,
        UUID tenantId,           // null in the response = this is a platform-default rule
        String module,
        String entityType,
        String name,
        boolean active,
        int priority,
        String conditions,
        String approvalMode,
        String approverChain,
        boolean platformDefault,
        Instant createdAt,
        Instant updatedAt
) {}