package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.CustomerCommunication;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerCommunicationRepository extends JpaRepository<CustomerCommunication, UUID> {

    @Query("""
            SELECT c FROM CustomerCommunication c
            WHERE c.tenantId   = :tenantId
              AND c.customerId = :customerId
            ORDER BY c.occurredAt DESC
            """)
    List<CustomerCommunication> findByCustomer(
            @Param("tenantId")   TenantId tenantId,
            @Param("customerId") UUID customerId
    );

    /** Tenant-scoped single lookup — same defence-in-depth convention as CustomerFollowUpRepository. */
    @Query("""
            SELECT c FROM CustomerCommunication c
            WHERE c.id = :id
              AND c.tenantId = :tenantId
            """)
    Optional<CustomerCommunication> findByIdAndTenant(
            @Param("tenantId") TenantId tenantId,
            @Param("id")       UUID id
    );
}