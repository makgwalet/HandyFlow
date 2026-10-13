package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;

/** Publishing an existing code creates its next version. Placeholders: {{debtorName}}, {{caseNumber}}, {{totalOutstanding}}, {{openedDate}}, {{custom.anything}}. */
public record PublishTemplateRequest(
        @NotBlank String code,
        @NotNull CommunicationChannel channel,
        String subject,
        @NotBlank String body
) {}
