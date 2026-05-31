package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    @Query("""
        SELECT c FROM Contract c WHERE c.tenantId = :#{#tenantId.value}
        AND c.deletedAt IS NULL
        AND (:status IS NULL OR c.status = :status)
        AND (:type IS NULL OR c.contractType = :type)
        ORDER BY c.createdAt DESC
        """)
    Page<Contract> findAllActive(TenantId tenantId, String status,
                                 String type, Pageable pageable);

    @Query("SELECT c FROM Contract c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.deletedAt IS NULL")
    Optional<Contract> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT c FROM Contract c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'SIGNED' AND c.endDate <= :today AND c.deletedAt IS NULL")
    List<Contract> findExpired(TenantId tenantId, LocalDate today);

    @Query("SELECT c FROM Contract c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'SIGNED' AND c.endDate = :alertDate AND c.deletedAt IS NULL")
    List<Contract> findExpiringOn(TenantId tenantId, LocalDate alertDate);

    @Query("SELECT COUNT(c) FROM Contract c WHERE c.tenantId = :#{#tenantId.value}")
    long countByTenant(TenantId tenantId);
}