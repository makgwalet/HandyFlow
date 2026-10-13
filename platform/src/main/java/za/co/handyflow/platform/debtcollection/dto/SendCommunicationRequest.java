package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationPurpose;

import java.util.Map;

/**
 * Send via SMS or EMAIL (letters use the letters endpoint). Either templateCode (latest version is used and
 * rendered from the case) or body (+ subject for email) is required. recipient defaults to the case's debtor
 * phone/email. variables may only supply "custom.*" placeholders. idempotencyKey makes client retries safe.
 * processingBasis is free text: the lawful basis/consent reference relied on (to be defined with counsel).
 */
public record SendCommunicationRequest(
        @NotNull CommunicationChannel channel,
        @NotNull CommunicationPurpose purpose,
        String recipient,
        String templateCode,
        String subject,
        String body,
        Map<String, String> variables,
        @Size(max = 100) String processingBasis,
        @Size(max = 100) String idempotencyKey
) {}
