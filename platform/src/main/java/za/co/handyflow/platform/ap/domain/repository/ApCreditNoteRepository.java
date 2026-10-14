package za.co.handyflow.platform.ap.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.ap.domain.model.ApCreditNote;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApCreditNoteRepository extends JpaRepository<ApCreditNote, UUID> {

    Optional<ApCreditNote> findByIdAndTenantId(UUID id, TenantId tenantId);

    @Query("SELECT c FROM ApCreditNote c WHERE c.tenantId = :tenantId ORDER BY c.createdAt DESC")
    List<ApCreditNote> findAllForTenant(TenantId tenantId);

    @Query("SELECT c FROM ApCreditNote c WHERE c.tenantId = :tenantId AND c.billId = :billId AND c.status <> 'REJECTED'")
    List<ApCreditNote> findLiveByBill(TenantId tenantId, UUID billId);

    @Query("""
        SELECT c FROM ApCreditNote c
        WHERE c.tenantId = :tenantId AND c.status = 'ISSUED' AND LOWER(c.supplierName) = LOWER(:supplierName)
          AND c.allocated < c.totalAmount
        ORDER BY c.creditDate ASC
        """)
    List<ApCreditNote> findWithCreditLeft(TenantId tenantId, String supplierName);

    @Query("""
        SELECT COALESCE(SUM(c.totalAmount - c.allocated), 0) FROM ApCreditNote c
        WHERE c.tenantId = :tenantId AND c.status = 'ISSUED' AND LOWER(c.supplierName) = LOWER(:supplierName)
        """)
    java.math.BigDecimal creditLeft(TenantId tenantId, String supplierName);

    @Query("""
        SELECT COUNT(c) > 0 FROM ApCreditNote c
        WHERE c.tenantId = :tenantId AND c.status <> 'REJECTED'
          AND LOWER(c.supplierName) = LOWER(:supplierName) AND LOWER(c.creditNoteNumber) = LOWER(:number)
        """)
    boolean existsLive(TenantId tenantId, String supplierName, String number);
}
