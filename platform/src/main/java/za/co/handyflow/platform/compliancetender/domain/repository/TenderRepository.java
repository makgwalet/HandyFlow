package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface TenderRepository extends JpaRepository<Tender, UUID> {

    @Query("SELECT t FROM Tender t WHERE t.tenantId.value = :#{#tenantId.value} AND t.id = :id")
    Optional<Tender> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM Tender t WHERE t.tenantId.value = :#{#tenantId.value} " +
           "AND (:status IS NULL OR t.status = :status) ORDER BY t.closingDate ASC NULLS LAST")
    Page<Tender> findAll(@Param("tenantId") TenantId tenantId, @Param("status") String status, Pageable pageable);
}
