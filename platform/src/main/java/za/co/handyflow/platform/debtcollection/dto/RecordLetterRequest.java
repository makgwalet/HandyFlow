package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationPurpose;
import za.co.handyflow.platform.debtcollection.domain.model.LetterDispatchMethod;

import java.time.Instant;
import java.util.UUID;

/** Record a letter staff have already dispatched. evidenceId optionally links proof of dispatch/delivery or a copy of the letter. */
public record RecordLetterRequest(
        @NotNull CommunicationPurpose purpose,
        @NotBlank String postalAddress,
        @NotBlank String subject,
        String summary,
        @NotNull LetterDispatchMethod dispatchMethod,
        String trackingReference,
        UUID evidenceId,
        Instant sentAt,
        String templateCode
) {}
