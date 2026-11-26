package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployeeChangeRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayEmployeeChangeRequestRepository extends JpaRepository<PayEmployeeChangeRequest, UUID> {

    @Query("SELECT c FROM PayEmployeeChangeRequest c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<PayEmployeeChangeRequest> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM PayEmployeeChangeRequest c WHERE c.tenantId = :tenantId AND c.payEmployeeId = :employeeId AND c.status = 'PENDING'")
    Optional<PayEmployeeChangeRequest> findPendingForEmployee(@Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    @Query("SELECT c FROM PayEmployeeChangeRequest c WHERE c.tenantId = :tenantId AND c.payClientId = :clientId ORDER BY c.requestedAt DESC")
    List<PayEmployeeChangeRequest> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT c.payEmployeeId FROM PayEmployeeChangeRequest c WHERE c.tenantId = :tenantId AND c.payClientId = :clientId AND c.status = 'PENDING'")
    List<UUID> pendingEmployeeIds(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);
}
