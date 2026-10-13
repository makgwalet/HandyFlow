package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.LegalNotice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalNoticeRepository extends JpaRepository<LegalNotice, UUID> {

    @Query("SELECT n FROM LegalNotice n WHERE n.tenantId = :tenantId AND n.id = :id")
    Optional<LegalNotice> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT n FROM LegalNotice n WHERE n.tenantId = :tenantId AND n.caseId = :caseId ORDER BY n.createdAt DESC")
    List<LegalNotice> findByCaseId(TenantId tenantId, UUID caseId);
}
