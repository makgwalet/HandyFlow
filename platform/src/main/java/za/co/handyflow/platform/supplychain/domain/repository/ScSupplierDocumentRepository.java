package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierDocType;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScSupplierDocumentRepository extends JpaRepository<ScSupplierDocument, UUID> {

    @Query("SELECT d FROM ScSupplierDocument d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<ScSupplierDocument> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT d FROM ScSupplierDocument d WHERE d.tenantId = :tenantId AND d.supplierId = :supplierId ORDER BY d.createdAt DESC")
    List<ScSupplierDocument> findAllForSupplier(@Param("tenantId") UUID tenantId, @Param("supplierId") UUID supplierId);

    @Query("SELECT d FROM ScSupplierDocument d WHERE d.tenantId = :tenantId AND d.supplierId = :supplierId AND d.status = 'ACTIVE' ORDER BY d.docType, d.createdAt DESC")
    List<ScSupplierDocument> findActiveForSupplier(@Param("tenantId") UUID tenantId, @Param("supplierId") UUID supplierId);

    @Query("SELECT d FROM ScSupplierDocument d WHERE d.tenantId = :tenantId AND d.supplierId = :supplierId AND d.docType = :type AND d.status = 'ACTIVE'")
    List<ScSupplierDocument> findActiveOfType(@Param("tenantId") UUID tenantId, @Param("supplierId") UUID supplierId,
                                              @Param("type") SupplierDocType type);

    @Query("SELECT d FROM ScSupplierDocument d WHERE d.tenantId = :tenantId AND d.status = 'ACTIVE'")
    List<ScSupplierDocument> findActiveForTenant(@Param("tenantId") UUID tenantId);

    /**
     * Cross-tenant daily sweep: ACTIVE suppliers with an expired document of a REQUIRED type and
     * no current replacement. Returns [tenant_id, supplier_id, supplier_name, doc_type, expiry_date].
     */
    @Query(value = """
            SELECT DISTINCT s.tenant_id, s.id, s.name, d.doc_type, d.expiry_date
            FROM sc_supplier_documents d
            JOIN sc_suppliers s ON s.id = d.supplier_id AND s.deleted_at IS NULL AND s.status = 'ACTIVE'
            WHERE d.status = 'ACTIVE' AND d.expiry_date < CURRENT_DATE AND d.doc_type IN (:types)
              AND NOT EXISTS (SELECT 1 FROM sc_supplier_documents d2
                              WHERE d2.supplier_id = d.supplier_id AND d2.doc_type = d.doc_type
                                AND d2.status = 'ACTIVE' AND (d2.expiry_date IS NULL OR d2.expiry_date >= CURRENT_DATE))
            ORDER BY s.tenant_id, s.name
            """, nativeQuery = true)
    List<Object[]> findExpiredRequired(@Param("types") List<String> requiredTypes);

    /** Weekly digest: documents (any type) expiring within N days on live suppliers. [tenant_id, supplier_name, doc_type, expiry_date]. */
    @Query(value = """
            SELECT s.tenant_id, s.name, d.doc_type, d.expiry_date
            FROM sc_supplier_documents d
            JOIN sc_suppliers s ON s.id = d.supplier_id AND s.deleted_at IS NULL AND s.status IN ('ACTIVE','SUSPENDED')
            WHERE d.status = 'ACTIVE' AND d.expiry_date >= CURRENT_DATE
              AND d.expiry_date <= CURRENT_DATE + CAST(:days AS integer)
            ORDER BY s.tenant_id, d.expiry_date
            """, nativeQuery = true)
    List<Object[]> findExpiringSoon(@Param("days") int days);
}
