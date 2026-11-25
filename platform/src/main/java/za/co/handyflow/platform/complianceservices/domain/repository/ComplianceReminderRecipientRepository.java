package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceReminderRecipient;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceReminderRecipientRepository extends JpaRepository<ComplianceReminderRecipient, UUID> {
    @Query("SELECT r FROM ComplianceReminderRecipient r WHERE r.tenantId.value = :#{#tenantId.value} ORDER BY r.createdAt, r.id")
    List<ComplianceReminderRecipient> findForTenant(@Param("tenantId") TenantId tenantId);
}
