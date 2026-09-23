package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPersonnel;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenderPersonnelRepository extends JpaRepository<TenderPersonnel, UUID> {

    @Query("SELECT p FROM TenderPersonnel p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<TenderPersonnel> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM TenderPersonnel p WHERE p.tenantId = :#{#tenantId.value} " +
           "AND p.tenderId = :tenderId ORDER BY p.createdAt")
    List<TenderPersonnel> findByTender(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);
}
