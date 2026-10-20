package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.IssueStatus;

public record UpdateIssueStatusRequest(
        @NotNull IssueStatus status,
        @Size(max = 4000) String note    // recorded as the resolution note when resolving / dismissing
) {}