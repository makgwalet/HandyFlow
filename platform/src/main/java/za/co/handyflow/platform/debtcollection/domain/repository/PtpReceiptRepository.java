package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.PtpReceipt;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface PtpReceiptRepository extends JpaRepository<PtpReceipt, UUID> {

    @Query("SELECT r FROM PtpReceipt r WHERE r.tenantId = :tenantId AND r.ptpId = :ptpId ORDER BY r.receivedDate ASC, r.createdAt ASC")
    List<PtpReceipt> findByPtpId(TenantId tenantId, UUID ptpId);
}
