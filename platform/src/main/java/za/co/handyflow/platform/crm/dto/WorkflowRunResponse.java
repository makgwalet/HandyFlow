package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkflowRunResponse(UUID id, UUID workflowId, String workflowName, UUID customerId, String customerName, String outcome, String detail, Instant ranAt) {}
