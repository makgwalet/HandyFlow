package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.shared.TenantId;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelSalesOrderRepository extends JpaRepository<FuelSalesOrder, UUID> {

    @Query("SELECT o FROM FuelSalesOrder o WHERE o.tenantId = :tenantId AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    Page<FuelSalesOrder> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT o FROM FuelSalesOrder o WHERE o.tenantId = :tenantId AND o.id = :id AND o.deletedAt IS NULL")
    Optional<FuelSalesOrder> findActiveById(TenantId tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM FuelSalesOrder o WHERE o.tenantId = :tenantId AND o.id = :id AND o.deletedAt IS NULL")
    Optional<FuelSalesOrder> findActiveByIdForUpdate(TenantId tenantId, UUID id);

    /** Orders that still owe fuel to this customer (confirmed or part delivered). */
    @Query("SELECT o FROM FuelSalesOrder o WHERE o.tenantId = :tenantId AND o.crmCustomerId = :customerId AND o.status IN ('CONFIRMED','PART_DELIVERED') AND o.deletedAt IS NULL")
    List<FuelSalesOrder> findOpenForCustomer(TenantId tenantId, UUID customerId);
}
