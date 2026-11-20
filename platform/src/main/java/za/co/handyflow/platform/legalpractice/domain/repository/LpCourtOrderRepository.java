package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpCourtOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpCourtOrderRepository extends JpaRepository<LpCourtOrder, UUID> {

    @Query("SELECT o FROM LpCourtOrder o WHERE o.tenantId = :tenantId AND o.id = :id")
    Optional<LpCourtOrder> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT o FROM LpCourtOrder o WHERE o.tenantId = :tenantId AND o.caseId = :caseId ORDER BY o.orderDate DESC, o.createdAt DESC")
    List<LpCourtOrder> findForCase(TenantId tenantId, UUID caseId);
}
