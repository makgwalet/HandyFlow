package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A formal record that specific document revisions were sent to a named recipient for a stated
 * purpose. Recording only — nothing is emailed from here. Items snapshot the document number,
 * title, revision and status at the moment of sending, so the record stays accurate no matter
 * what later happens to those documents.
 */
@Entity
@Table(name = "project_transmittals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTransmittal {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)          private UUID tenantId;
    @Column(name = "project_id", nullable = false)         private UUID projectId;
    @Column(name = "transmittal_number", nullable = false) private String transmittalNumber;
    @Column(name = "subject", nullable = false)            private String subject;
    @Column(name = "recipient_name", nullable = false)     private String recipientName;
    @Column(name = "recipient_organisation")               private String recipientOrganisation;
    @Column(name = "recipient_email")                      private String recipientEmail;
    @Column(name = "purpose", nullable = false)            private String purpose;
    @Column(name = "notes")                                private String notes;
    @Column(name = "sent_by")                              private UUID sentBy;
    @Column(name = "sent_by_name")                         private String sentByName;
    @Column(name = "sent_at", nullable = false)            private Instant sentAt;
    @Column(name = "acknowledged_at")                      private Instant acknowledgedAt;
    @Column(name = "acknowledged_by")                      private String acknowledgedBy;

    public static ProjectTransmittal create(UUID tenantId, UUID projectId, String number, String subject,
                                            String recipientName, String recipientOrganisation, String recipientEmail,
                                            String purpose, String notes, UUID sentBy, String sentByName) {
        ProjectTransmittal t = new ProjectTransmittal();
        t.id                     = UUID.randomUUID();
        t.tenantId               = tenantId;
        t.projectId              = projectId;
        t.transmittalNumber      = number;
        t.subject                = subject;
        t.recipientName          = recipientName;
        t.recipientOrganisation  = recipientOrganisation;
        t.recipientEmail         = recipientEmail;
        t.purpose                = purpose;
        t.notes                  = notes;
        t.sentBy                 = sentBy;
        t.sentByName             = sentByName;
        t.sentAt                 = Instant.now();
        return t;
    }

    /** Recorded once; a second acknowledgement is refused so the original isn't overwritten. */
    public void acknowledge(String by) {
        if (acknowledgedAt != null) throw new IllegalStateException("This transmittal has already been acknowledged");
        this.acknowledgedAt = Instant.now();
        this.acknowledgedBy = by;
    }
}