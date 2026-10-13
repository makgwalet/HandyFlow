package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.PtpEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface PtpEventRepository extends JpaRepository<PtpEvent, UUID> {

    @Query("SELECT e FROM PtpEvent e WHERE e.tenantId = :tenantId AND e.ptpId = :ptpId ORDER BY e.createdAt ASC")
    List<PtpEvent> findByPtpId(TenantId tenantId, UUID ptpId);
}
