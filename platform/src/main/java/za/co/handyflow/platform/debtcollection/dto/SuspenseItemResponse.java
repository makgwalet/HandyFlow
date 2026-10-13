package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseItem;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseKind;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The raw payload is not exposed here; counterparty/body hold the extracted payer reference or sender and message. */
public record SuspenseItemResponse(UUID id, SuspenseKind kind, SuspenseStatus status, String reason, String externalReference,
                                   BigDecimal amount, String currency, LocalDate eventDate, CommunicationChannel channel,
                                   String counterparty, String body, UUID resolvedCaseId, String resolutionNote,
                                   Instant resolvedAt, Instant createdAt) {
    public static SuspenseItemResponse of(SuspenseItem s) {
        return new SuspenseItemResponse(s.getId(), s.getKind(), s.getStatus(), s.getReason(), s.getExternalReference(),
                s.getAmount(), s.getCurrency(), s.getEventDate(), s.getChannel(), s.getCounterparty(), s.getBody(),
                s.getResolvedCaseId(), s.getResolutionNote(), s.getResolvedAt(), s.getCreatedAt());
    }
}
