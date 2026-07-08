package za.co.handyflow.platform.creative.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ConfigureApprovalRequest(
        @Pattern(regexp = "SEQUENTIAL|PARALLEL", message = "mode must be SEQUENTIAL or PARALLEL")
        String mode,
        @NotEmpty(message = "at least one approver is required")
        @Valid
        List<AddApproverRequest> approvers
) {}
