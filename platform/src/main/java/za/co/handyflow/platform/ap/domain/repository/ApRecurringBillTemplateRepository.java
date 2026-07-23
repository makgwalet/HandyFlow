package za.co.handyflow.platform.ap.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.ap.domain.model.ApRecurringBillTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApRecurringBillTemplateRepository extends JpaRepository<ApRecurringBillTemplate, UUID> {

    @Query("""
        SELECT t FROM ApRecurringBillTemplate t
        WHERE t.tenantId = :tenantId
        AND t.deletedAt IS NULL
        ORDER BY t.nextDueDate ASC
        """)
    List<ApRecurringBillTemplate> findAll(TenantId tenantId);

    Optional<ApRecurringBillTemplate> findByIdAndTenantId(UUID id, TenantId tenantId);

    // Cross-tenant, used by ApRecurringBillScheduler — no tenant param
    // since the scheduler runs once for the whole platform, same pattern
    // as ApBillRepository.findAllOverdueAcrossTenants()/
    // findDueSoonAcrossTenants(). leadDays varies per template, so the
    // "is this actually due yet" check happens in the service after
    // fetching, not as a single WHERE clause here.
    @Query("""
        SELECT t FROM ApRecurringBillTemplate t
        WHERE t.active = true
        AND t.deletedAt IS NULL
        """)
    List<ApRecurringBillTemplate> findAllActiveAcrossTenants();
}