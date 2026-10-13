package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;

import java.time.Instant;

/** A debtor's reply. providerMessageId (optional) makes recording idempotent. */
public record RecordInboundRequest(
        @NotNull CommunicationChannel channel,
        @NotBlank String from,
        @NotBlank String body,
        Instant receivedAt,
        String providerMessageId
) {}
