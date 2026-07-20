package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccFicaDocument;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccFicaDocumentRepository extends JpaRepository<AccFicaDocument, UUID> {

    @Query("""
        SELECT d FROM AccountantFicaDocument d
        WHERE d.tenantId = :tenantId
          AND d.id = :id
    """)
    Optional<AccFicaDocument> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Metadata-only Spring Data interface projection for list views —
     * Hibernate only SELECTs the listed columns, so file_content_base64
     * is never fetched here. Same reasoning as
     * ScSupplierInvoiceAttachmentRepository.AttachmentSummaryProjection
     * in the Supply Chain module.
     */
    interface FicaDocSummaryProjection {
        UUID getId();
        String getDocType();
        String getFileName();
        String getContentType();
        Long getFileSizeBytes();
        boolean isVerified();
        Instant getVerifiedAt();
        LocalDate getExpiryDate();
        String getUploadedByName();
        // NEW: closes the ambiguity flagged when scoping client-portal
        // upload.
        String getUploadedByType();
        Instant getCreatedAt();
    }

    @Query("""
        SELECT d.id as id, d.docType as docType, d.fileName as fileName, d.contentType as contentType,
               d.fileSizeBytes as fileSizeBytes, d.verified as verified, d.verifiedAt as verifiedAt,
               d.expiryDate as expiryDate, d.uploadedByName as uploadedByName,
               d.uploadedByType as uploadedByType, d.createdAt as createdAt
        FROM AccountantFicaDocument d
        WHERE d.tenantId = :tenantId
          AND d.clientId = :clientId
        ORDER BY d.createdAt DESC
    """)
    List<FicaDocSummaryProjection> findSummariesByClient(@Param("tenantId") UUID tenantId,
                                                         @Param("clientId") UUID clientId);

    /**
     * NEW: closes the "FICA expiry reminders" gap — replaces the
     * earlier placeholder findExpiringOn(), which had no idempotency
     * tracking at all (would have fired every single day a document
     * sat within any arbitrary "expiring soon" window, not just once
     * per tier). Global (no tenant filter), matching
     * AccClientRepository's own TCS PIN reminder query pattern — the
     * daily scheduler processes every tenant's documents in one query,
     * then resolves each document's own tenant/client to find the
     * right firm email to notify.
     */
    @Query("""
        SELECT d FROM AccountantFicaDocument d
        WHERE d.expiryDate = :targetDate
          AND d.reminder30Sent = false
    """)
    List<AccFicaDocument> findPendingReminder30(@Param("targetDate") LocalDate targetDate);

    @Query("""
        SELECT d FROM AccountantFicaDocument d
        WHERE d.expiryDate = :targetDate
          AND d.reminder7Sent = false
    """)
    List<AccFicaDocument> findPendingReminder7(@Param("targetDate") LocalDate targetDate);

    @Query("""
        SELECT d FROM AccountantFicaDocument d
        WHERE d.expiryDate = :targetDate
          AND d.reminder1Sent = false
    """)
    List<AccFicaDocument> findPendingReminder1(@Param("targetDate") LocalDate targetDate);
}