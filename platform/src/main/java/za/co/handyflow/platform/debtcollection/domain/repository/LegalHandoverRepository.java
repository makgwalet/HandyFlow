package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.LegalHandover;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalHandoverRepository extends JpaRepository<LegalHandover, UUID> {

    @Query("SELECT h FROM LegalHandover h WHERE h.tenantId = :tenantId AND h.id = :id")
    Optional<LegalHandover> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT h FROM LegalHandover h WHERE h.tenantId = :tenantId AND h.caseId = :caseId ORDER BY h.createdAt DESC")
    List<LegalHandover> findByCaseId(TenantId tenantId, UUID caseId);

    @Query("SELECT h FROM LegalHandover h WHERE h.tenantId = :tenantId AND h.caseId = :caseId AND h.status IN ('DRAFT','IN_REVIEW','APPROVED')")
    List<LegalHandover> findOpenByCaseId(TenantId tenantId, UUID caseId);

    @Query("SELECT h FROM LegalHandover h WHERE h.tenantId = :tenantId AND h.legalMatterId = :matterId")
    Optional<LegalHandover> findByLegalMatterId(TenantId tenantId, UUID matterId);
}
