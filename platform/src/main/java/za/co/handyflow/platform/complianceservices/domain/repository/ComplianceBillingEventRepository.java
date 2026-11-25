package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceBillingEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceBillingEventRepository extends JpaRepository<ComplianceBillingEvent, UUID> {

    @Query("SELECT e FROM ComplianceBillingEvent e WHERE e.tenantId.value = :#{#tenantId.value} AND e.id = :id")
    Optional<ComplianceBillingEvent> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT e FROM ComplianceBillingEvent e WHERE e.tenantId.value = :#{#tenantId.value} AND e.agreementId = :agreementId ORDER BY e.dueDate")
    List<ComplianceBillingEvent> findForAgreement(@Param("tenantId") TenantId tenantId, @Param("agreementId") UUID agreementId);

    @Query("SELECT e FROM ComplianceBillingEvent e WHERE e.tenantId.value = :#{#tenantId.value} ORDER BY e.dueDate, e.createdAt")
    List<ComplianceBillingEvent> findAllForTenant(@Param("tenantId") TenantId tenantId);

    /** Bills that have been raised as an invoice and fall due in the period. */
    @Query("SELECT e FROM ComplianceBillingEvent e WHERE e.tenantId.value = :#{#tenantId.value} AND e.invoiceId IS NOT NULL AND e.dueDate BETWEEN :from AND :to")
    List<ComplianceBillingEvent> findInvoicedBetween(@Param("tenantId") TenantId tenantId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}
