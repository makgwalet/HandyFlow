package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectTransmittal;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransmittalResponse(
        UUID id, String transmittalNumber, String subject,
        String recipientName, String recipientOrganisation, String recipientEmail,
        String purpose, String notes, String sentByName, Instant sentAt,
        Instant acknowledgedAt, String acknowledgedBy, List<Item> items,
        List<TransmittalRecipientResponse> recipients
) {
    public record Item(UUID documentId, String documentNumber, String title, String revision, String statusAtSend,
                       String fileName, String fileSha256) {}

    public static TransmittalResponse of(ProjectTransmittal t, List<ProjectTransmittalItem> items) {
        return of(t, items, List.of());
    }

    public static TransmittalResponse of(ProjectTransmittal t, List<ProjectTransmittalItem> items,
                                         List<TransmittalRecipientResponse> recipients) {
        return new TransmittalResponse(t.getId(), t.getTransmittalNumber(), t.getSubject(),
                t.getRecipientName(), t.getRecipientOrganisation(), t.getRecipientEmail(),
                t.getPurpose(), t.getNotes(), t.getSentByName(), t.getSentAt(),
                t.getAcknowledgedAt(), t.getAcknowledgedBy(),
                items.stream().map(i -> new Item(i.getDocumentId(), i.getDocumentNumber(),
                        i.getTitle(), i.getRevision(), i.getStatusAtSend(),
                        i.getFileName(), i.getFileSha256())).toList(),
                recipients);
    }
}