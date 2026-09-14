package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record CreateFindingRequest(
        UUID sourceExceptionId, // nullable -- can be raised directly, not only promoted from a failed test
        @NotBlank String title,
        @NotBlank String description,
        String rootCause,
        String recommendation,
        @NotBlank String severity, // LOW | MEDIUM | HIGH | CRITICAL -- independent of the engagement's risk level, same hard constraint as AuditException
        UUID owner,
        LocalDate dueDate
) {}
