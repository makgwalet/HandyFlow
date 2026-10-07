package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPackageDraft;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface TenderPackageDraftRepository extends JpaRepository<TenderPackageDraft, UUID> {
    @Query("SELECT d FROM TenderPackageDraft d WHERE d.tenantId.value = :#{#tenantId.value} AND d.tenderId = :tenderId")
    Optional<TenderPackageDraft> findByTender(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);
}
