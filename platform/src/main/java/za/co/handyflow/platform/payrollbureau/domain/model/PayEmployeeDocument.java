package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A document on file for one of the bureau's employees — ID copy, IRP5
 * from a prior employer, banking confirmation letter, signed contract.
 * Bytes live in shared.FileStorageService (storageKey is opaque — see
 * that interface's own Javadoc for why callers must never construct or
 * parse it); this entity only holds the metadata.
 */
@Entity
@Table(name = "pay_employee_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayEmployeeDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_employee_id", nullable = false)
    private UUID payEmployeeId;

    @Column(name = "doc_type", nullable = false)
    private String docType; // ID_COPY | TAX_CERTIFICATE_IRP5 | BANKING_CONFIRMATION | CONTRACT | OTHER

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public static PayEmployeeDocument create(UUID tenantId, UUID payEmployeeId, String docType,
                                             String fileName, String contentType,
                                             String storageKey, long fileSizeBytes) {
        PayEmployeeDocument d = new PayEmployeeDocument();
        d.tenantId = tenantId;
        d.payEmployeeId = payEmployeeId;
        d.docType = docType;
        d.fileName = fileName;
        d.contentType = contentType;
        d.storageKey = storageKey;
        d.fileSizeBytes = fileSizeBytes;
        d.uploadedAt = Instant.now();
        return d;
    }
}