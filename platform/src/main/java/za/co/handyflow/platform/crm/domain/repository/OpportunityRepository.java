package za.co.handyflow.platform.crm.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.Opportunity;
import za.co.handyflow.platform.shared.TenantId;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

    @Query("SELECT o FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL AND o.id = :id")
    Optional<Opportunity> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query(value = "SELECT o FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL ORDER BY o.createdAt DESC, o.id",
            countQuery = "SELECT COUNT(o) FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL")
    Page<Opportunity> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query(value = "SELECT o FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL AND o.customerId = :customerId ORDER BY o.createdAt DESC, o.id",
            countQuery = "SELECT COUNT(o) FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL AND o.customerId = :customerId")
    Page<Opportunity> findActiveByCustomer(@Param("tenantId") TenantId tenantId, @Param("customerId") UUID customerId, Pageable pageable);

    /** Open = not won and not lost. */
    @Query(value = "SELECT o FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL AND o.stage NOT IN (za.co.handyflow.platform.crm.domain.model.OpportunityStage.WON, za.co.handyflow.platform.crm.domain.model.OpportunityStage.LOST) ORDER BY o.expectedCloseDate ASC NULLS LAST, o.id",
            countQuery = "SELECT COUNT(o) FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL AND o.stage NOT IN (za.co.handyflow.platform.crm.domain.model.OpportunityStage.WON, za.co.handyflow.platform.crm.domain.model.OpportunityStage.LOST)")
    Page<Opportunity> findOpen(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("SELECT o FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL")
    List<Opportunity> findEveryActive(@Param("tenantId") TenantId tenantId);

    @Query("SELECT o FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL AND o.customerId IN :customerIds")
    List<Opportunity> findActiveForCustomers(@Param("tenantId") TenantId tenantId, @Param("customerIds") Collection<UUID> customerIds);

    @Query("SELECT COUNT(o) FROM Opportunity o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL AND o.customerId = :customerId")
    long countActiveForCustomer(@Param("tenantId") TenantId tenantId, @Param("customerId") UUID customerId);
}
