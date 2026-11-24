package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ClientComplianceDocumentLifecycleDtos {

    private ClientComplianceDocumentLifecycleDtos() {}

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record DocumentEventView(String event, String fromStatus, String toStatus, String reason, UUID actor, Instant occurredAt) {}
}
