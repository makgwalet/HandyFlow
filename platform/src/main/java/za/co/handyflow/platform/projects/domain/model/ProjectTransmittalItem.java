package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "project_transmittal_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTransmittalItem {

    @Id private UUID id;
    @Column(name = "transmittal_id", nullable = false) private UUID transmittalId;
    @Column(name = "document_id", nullable = false)    private UUID documentId;
    @Column(name = "document_number")                   private String documentNumber;
    @Column(name = "title", nullable = false)           private String title;
    @Column(name = "revision")                          private String revision;
    @Column(name = "status_at_send", nullable = false)  private String statusAtSend;

    public static ProjectTransmittalItem snapshot(UUID transmittalId, ProjectDocument d) {
        ProjectTransmittalItem i = new ProjectTransmittalItem();
        i.id             = UUID.randomUUID();
        i.transmittalId  = transmittalId;
        i.documentId     = d.getId();
        i.documentNumber = d.getDocumentNumber();
        i.title          = d.getTitle();
        i.revision       = d.getRevision();
        i.statusAtSend   = d.getStatus();
        return i;
    }
}