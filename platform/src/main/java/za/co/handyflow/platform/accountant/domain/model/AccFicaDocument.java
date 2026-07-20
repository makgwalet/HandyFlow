package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A FICA/KYC document on a client record (ID copy, proof of address,
 * beneficial ownership, company documents, trust deed, other). Maps to
 * acc_fica_documents — a table that already existed
 * (V58__accountant_module.sql) before this feature was built; it just
 * had no application-layer code (entity/repository/service/controller)
 * at all. Confirmed by reading the real migration directly, not assumed
 * — same discipline already applied after the acc_payments_received
 * collision earlier in this module's work.
 * <p>
 * storage_key (VARCHAR(500)) already existed on the real table, clearly
 * intended for a real object-storage key once available. There's no S3
 * in this environment yet, so file_content_base64 and friends were
 * added via migration to store content directly for now — storage_key
 * itself is left alone, reserved and unused, not repurposed to hold
 * something it wasn't designed for.
 * <p>
 * docType is validated by the database's own CHECK constraint
 * (ID_COPY, PROOF_OF_ADDRESS, BENEFICIAL_OWNERSHIP, COMPANY_DOCUMENTS,
 * TRUST_DEED, OTHER) — not re-validated at the application layer,
 * matching how this module already treats other status-like string
 * columns (FeeNote.status, TaxDeadline.status) as DB-constrained rather
 * than app-enum-validated.
 */
@Entity(name = "AccountantFicaDocument")
@Table(name = "acc_fica_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccFicaDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "doc_type",  nullable = false) private String docType;
    @Column(name = "file_name", length = 300) private String fileName;

    // Reserved for real object storage once available — never written
    // to by this feature. See class Javadoc.
    @Column(name = "storage_key", length = 500) private String storageKey;

    @Column(name = "file_content_base64", columnDefinition = "TEXT") private String fileContentBase64;
    @Column(name = "content_type", length = 100) private String contentType;
    @Column(name = "file_size_bytes") private Long fileSizeBytes;
    @Column(name = "uploaded_by")      private UUID uploadedBy;
    @Column(name = "uploaded_by_name", length = 255) private String uploadedByName;
    // NEW: closes the ambiguity flagged when scoping client-portal
    // upload — without this, "who uploaded this" was unanswerable once
    // portal users could upload too.
    @Column(name = "uploaded_by_type", length = 15) private String uploadedByType;

    @Column(name = "verified", nullable = false) private boolean verified = false;
    @Column(name = "verified_by") private UUID verifiedBy;
    @Column(name = "verified_at") private Instant verifiedAt;

    @Column(name = "expiry_date") private LocalDate expiryDate;

    // NEW: closes the "FICA expiry reminders" gap. Same belt-and-braces
    // pattern as AccClient's own TCS PIN reminder flags — see the
    // migration's own comment for why both a flag and an exact-date
    // match are used together.
    @Column(name = "reminder_30_sent", nullable = false) private boolean reminder30Sent = false;
    @Column(name = "reminder_7_sent",  nullable = false) private boolean reminder7Sent  = false;
    @Column(name = "reminder_1_sent",  nullable = false) private boolean reminder1Sent  = false;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static AccFicaDocument create(UUID tenantId, UUID clientId, String docType,
                                         String fileName, String contentType, Long fileSizeBytes,
                                         String fileContentBase64, LocalDate expiryDate,
                                         UUID uploadedBy, String uploadedByName, String uploadedByType) {
        if (fileContentBase64 == null || fileContentBase64.isBlank()) {
            throw new IllegalArgumentException("File content is required");
        }
        AccFicaDocument d = new AccFicaDocument();
        d.tenantId           = tenantId;
        d.clientId            = clientId;
        d.docType             = docType;
        d.fileName            = fileName;
        d.contentType         = contentType;
        d.fileSizeBytes       = fileSizeBytes;
        d.fileContentBase64   = fileContentBase64;
        d.expiryDate          = expiryDate;
        d.uploadedBy          = uploadedBy;
        d.uploadedByName      = uploadedByName;
        d.uploadedByType      = uploadedByType;
        d.createdAt           = Instant.now();
        return d;
    }

    public void markVerified(UUID verifiedBy) {
        this.verified   = true;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = Instant.now();
    }

    public void markReminder30Sent() { this.reminder30Sent = true; }
    public void markReminder7Sent()  { this.reminder7Sent  = true; }
    public void markReminder1Sent()  { this.reminder1Sent  = true; }
}