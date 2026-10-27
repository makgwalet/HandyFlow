package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code reason} is internal; {@code clientMessage} (optional) is what the client sees in the portal. */
public record FmRejectRequest(@NotBlank @Size(max = 1000) String reason, @Size(max = 1000) String clientMessage) {}
