package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

public record RaiseExceptionRequest(
        @NotBlank String description,
        @NotBlank String severity // LOW | MEDIUM | HIGH | CRITICAL -- deliberately independent of the test/engagement's own risk level, per the agreed hard constraint
) {}
