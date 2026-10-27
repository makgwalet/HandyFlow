package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import za.co.handyflow.platform.facilities.domain.model.FacilityPurchaseOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityPurchaseOrderRepository extends JpaRepository<FacilityPurchaseOrder, UUID> {

    @Query("SELECT p FROM FacilityPurchaseOrder p WHERE p.tenantId.value = :#{#tenantId.value} AND p.id = :id")
    Optional<FacilityPurchaseOrder> findForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    /** Locked so two people receiving the same delivery cannot both book it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FacilityPurchaseOrder p WHERE p.tenantId.value = :#{#tenantId.value} AND p.id = :id")
    Optional<FacilityPurchaseOrder> findForUpdate(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    /** {@code status} is a status name, or {@code ALL}. */
    @Query("SELECT p FROM FacilityPurchaseOrder p WHERE p.tenantId.value = :#{#tenantId.value} "
            + "AND (:status = 'ALL' OR p.status = :status) ORDER BY p.createdAt DESC")
    Page<FacilityPurchaseOrder> list(@Param("tenantId") TenantId tenantId, @Param("status") String status, Pageable pageable);

    /** Open (approved or part-received) orders; used to work out what is already on order. */
    @Query("SELECT p FROM FacilityPurchaseOrder p WHERE p.tenantId.value = :#{#tenantId.value} "
            + "AND p.status IN ('APPROVED','PARTLY_RECEIVED')")
    List<FacilityPurchaseOrder> findOpen(@Param("tenantId") TenantId tenantId);
}
