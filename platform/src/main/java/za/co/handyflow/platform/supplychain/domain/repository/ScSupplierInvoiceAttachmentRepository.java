package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoiceAttachment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScSupplierInvoiceAttachmentRepository extends JpaRepository<ScSupplierInvoiceAttachment, UUID> {

    @Query("SELECT a FROM ScSupplierInvoiceAttachment a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<ScSupplierInvoiceAttachment> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Metadata-only Spring Data interface projection for list views —
     * Hibernate only SELECTs the listed columns, so the file_content_base64
     * TEXT column is never fetched here. Same reasoning Creative's own
     * ProofResponse already applied (excluding fileUrl from list
     * responses), just enforced at the query level instead of only at the
     * response-DTO level, so the blob is never even pulled off disk for a
     * list call, not just kept out of the JSON.
     */
    interface AttachmentSummaryProjection {
        UUID getId();
        String getFileName();
        String getContentType();
        long getFileSizeBytes();
        String getUploadedByName();
        Instant getCreatedAt();
    }

    @Query("SELECT a.id as id, a.fileName as fileName, a.contentType as contentType, " +
            "a.fileSizeBytes as fileSizeBytes, a.uploadedByName as uploadedByName, a.createdAt as createdAt " +
            "FROM ScSupplierInvoiceAttachment a " +
            "WHERE a.tenantId = :tenantId AND a.supplierInvoiceId = :invoiceId ORDER BY a.createdAt DESC")
    List<AttachmentSummaryProjection> findSummariesByInvoice(@Param("tenantId") UUID tenantId,
                                                             @Param("invoiceId") UUID invoiceId);
}