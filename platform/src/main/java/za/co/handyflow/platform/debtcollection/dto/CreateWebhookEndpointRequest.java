package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.debtcollection.domain.model.WebhookPurpose;

public record CreateWebhookEndpointRequest(@NotBlank @Size(max = 100) String name, @NotNull WebhookPurpose purpose) {}
