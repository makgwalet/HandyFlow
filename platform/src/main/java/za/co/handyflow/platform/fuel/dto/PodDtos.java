package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Proof of delivery "received by" policy and its authorised exceptions. */
public final class PodDtos {
    private PodDtos() {}

    public record PodPolicy(boolean enforced, List<String> allowedExceptions) {}

    public record PodPolicyRequest(boolean enforced, List<String> allowedExceptions) {}

    public record ApproveRequest(@NotBlank String type, @NotBlank @Size(min = 10, max = 500) String reason) {}

    public record SitePodRequest(String exceptionType) {}

    public record PodExceptionView(UUID id, UUID deliveryId, String type, String reason, String status, UUID approvedBy, Instant approvedAt, Instant usedAt, int evidenceCount) {}
}
