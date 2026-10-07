// security/domain/repository/GuardComplaintEventRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardComplaintEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface GuardComplaintEventRepository extends JpaRepository<GuardComplaintEvent, UUID> {

    @Query("SELECT e FROM GuardComplaintEvent e WHERE e.tenantId = :tenantId AND e.complaintId = :complaintId ORDER BY e.at ASC")
    List<GuardComplaintEvent> findForComplaint(TenantId tenantId, UUID complaintId);
}
