package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.Contact;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    /** A customer's live contacts. Ordering is done by the caller (main contact first). */
    @Query("""
            SELECT c FROM Contact c
            WHERE c.tenantId = :tenantId
              AND c.customer.id = :customerId
              AND c.deletedAt IS NULL
            """)
    List<Contact> findActiveByCustomer(@Param("tenantId") TenantId tenantId, @Param("customerId") UUID customerId);

    @Query("""
            SELECT c FROM Contact c
            WHERE c.id = :id
              AND c.customer.id = :customerId
              AND c.tenantId = :tenantId
              AND c.deletedAt IS NULL
            """)
    Optional<Contact> findActiveById(@Param("tenantId") TenantId tenantId,
                                     @Param("customerId") UUID customerId,
                                     @Param("id") UUID id);
}
