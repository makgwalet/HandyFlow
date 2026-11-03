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
    /** The file as it was when sent (null for a link-only entry or for items sent before files were stored). */
    @Column(name = "file_name")                         private String fileName;
    @Column(name = "file_sha256")                       private String fileSha256;

    public static ProjectTransmittalItem snapshot(UUID transmittalId, ProjectDocument d) {
        ProjectTransmittalItem i = new ProjectTransmittalItem();
        i.id             = UUID.randomUUID();
        i.transmittalId  = transmittalId;
        i.documentId     = d.getId();
        i.documentNumber = d.getDocumentNumber();
        i.title          = d.getTitle();
        i.revision       = d.getRevision();
        i.statusAtSend   = d.getStatus();
        i.fileName       = d.getFileName();
        i.fileSha256     = d.getFileSha256();
        return i;
    }
}