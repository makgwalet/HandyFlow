package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.accountant.domain.model.AccFicaDocument;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-tenant COUNTS of FICA documents that have expired or are about to, for the Legal Compliance cross-module view. Kept apart from AccFicaDocumentRepository so that file is untouched.
 * Only documents of clients that still exist are counted, and a document is not counted when the same client has another document of the same type that replaces it
 * (no expiry, or one that lasts longer). Counts documents only: no names, no file contents.
 */
public interface AccFicaComplianceCounts extends Repository<AccFicaDocument, UUID> {

    @Query("""
        SELECT COUNT(d) FROM AccountantFicaDocument d
        WHERE d.tenantId = :tenantId AND d.expiryDate < :today
          AND EXISTS (SELECT 1 FROM AccountantClient c WHERE c.id = d.clientId AND c.deletedAt IS NULL)
          AND NOT EXISTS (SELECT 1 FROM AccountantFicaDocument n WHERE n.tenantId = d.tenantId AND n.clientId = d.clientId AND n.docType = d.docType AND n.id <> d.id
                          AND (n.expiryDate IS NULL OR n.expiryDate >= :today))
        """)
    long expiredNotReplaced(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today);

    @Query("""
        SELECT COUNT(d) FROM AccountantFicaDocument d
        WHERE d.tenantId = :tenantId AND d.expiryDate >= :today AND d.expiryDate <= :limit
          AND EXISTS (SELECT 1 FROM AccountantClient c WHERE c.id = d.clientId AND c.deletedAt IS NULL)
          AND NOT EXISTS (SELECT 1 FROM AccountantFicaDocument n WHERE n.tenantId = d.tenantId AND n.clientId = d.clientId AND n.docType = d.docType AND n.id <> d.id
                          AND (n.expiryDate IS NULL OR n.expiryDate > d.expiryDate))
        """)
    long expiringNotReplaced(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today, @Param("limit") LocalDate limit);
}
