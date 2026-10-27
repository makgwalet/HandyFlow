package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityApprovalLimit;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityApprovalLimitRepository extends JpaRepository<FacilityApprovalLimit, UUID> {

    @Query("SELECT l FROM FacilityApprovalLimit l WHERE l.tenantId.value = :#{#tenantId.value} AND l.userId = :userId")
    Optional<FacilityApprovalLimit> findForUser(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId);

    @Query("SELECT l FROM FacilityApprovalLimit l WHERE l.tenantId.value = :#{#tenantId.value} ORDER BY l.maxOrderValue ASC")
    List<FacilityApprovalLimit> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT COUNT(l) > 0 FROM FacilityApprovalLimit l WHERE l.tenantId.value = :#{#tenantId.value}")
    boolean anyForTenant(@Param("tenantId") TenantId tenantId);
}
