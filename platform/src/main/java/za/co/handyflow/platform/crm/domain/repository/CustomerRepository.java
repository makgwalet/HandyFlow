package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Query("""
        SELECT c FROM Customer c
        WHERE c.tenantId = :tenantId
        AND c.deletedAt IS NULL
        ORDER BY c.name
        """)
    Page<Customer> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT c FROM Customer c
        WHERE c.tenantId = :tenantId
        AND c.deletedAt IS NULL
        AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY c.name
        """)
    Page<Customer> searchActive(TenantId tenantId, String search, Pageable pageable);

    @Query("""
        SELECT c FROM Customer c
        WHERE c.tenantId = :tenantId
        AND c.id = :id
        AND c.deletedAt IS NULL
        """)
    Optional<Customer> findActiveById(TenantId tenantId, UUID id);

    boolean existsByTenantIdAndEmailAndDeletedAtIsNull(TenantId tenantId, String email);
}
