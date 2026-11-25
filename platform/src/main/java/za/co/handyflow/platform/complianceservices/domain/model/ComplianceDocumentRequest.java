package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The practice asking a client for a document. Closed or cancelled by the practice with a reason; nothing is deleted. */
@Entity(name = "ComplianceDocumentRequest")
@Table(name = "compliance_document_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceDocumentRequest {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "document_type")
    private String documentType;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(nullable = false)
    private String status = "OPEN";
    @Column(name = "requested_by")
    private UUID requestedBy;
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;
    @Column(name = "document_id")
    private UUID documentId;
    @Column(name = "closed_by")
    private UUID closedBy;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "close_note", columnDefinition = "TEXT")
    private String closeNote;
    @Version
    private Long version;

    public static ComplianceDocumentRequest create(TenantId tenantId, UUID clientId, String title, String description, String documentType, LocalDate dueDate, LocalDate today, UUID by) {
        ComplianceDocumentRequest r = new ComplianceDocumentRequest();
        r.tenantId = tenantId;
        r.clientId = clientId;
        r.title = ContactRules.text(title, "What you are asking for", 200, true);
        r.description = ContactRules.text(description, "The description", ContactRules.MAX_BODY, false);
        r.documentType = ContactRules.text(documentType, "The kind of document", 100, false);
        r.dueDate = ContactRules.dueDate(dueDate, today);
        r.requestedBy = by;
        r.requestedAt = Instant.now();
        return r;
    }

    public boolean isLive() { return "OPEN".equals(status) || "RECEIVED".equals(status); }

    public void close(UUID by, String note) { finish("CLOSED", by, note); }

    public void cancel(UUID by, String note) { finish("CANCELLED", by, note); }

    private void finish(String to, UUID by, String note) {
        if (!isLive()) throw new IllegalStateException("This request is already " + status.toLowerCase());
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        this.closeNote = ContactRules.closeNote(note);
        this.status = to;
        this.closedBy = by;
        this.closedAt = Instant.now();
    }
}
