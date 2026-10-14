package za.co.handyflow.platform.ap.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.ap.domain.model.ApCreditAllocation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ApCreditAllocationRepository extends JpaRepository<ApCreditAllocation, UUID> {

    @Query("SELECT a FROM ApCreditAllocation a WHERE a.tenantId = :tenantId AND a.creditNoteId = :creditNoteId ORDER BY a.allocatedAt ASC")
    List<ApCreditAllocation> findByCreditNote(TenantId tenantId, UUID creditNoteId);

    @Query("SELECT a FROM ApCreditAllocation a WHERE a.tenantId = :tenantId AND a.billId = :billId ORDER BY a.allocatedAt ASC")
    List<ApCreditAllocation> findByBill(TenantId tenantId, UUID billId);
}
