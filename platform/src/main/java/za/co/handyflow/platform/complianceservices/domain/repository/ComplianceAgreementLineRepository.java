package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceAgreementLine;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceAgreementLineRepository extends JpaRepository<ComplianceAgreementLine, UUID> {

    @Query("SELECT l FROM ComplianceAgreementLine l WHERE l.tenantId.value = :#{#tenantId.value} AND l.agreementId IN :agreementIds ORDER BY l.agreementId, l.sortOrder")
    List<ComplianceAgreementLine> findForAgreements(@Param("tenantId") TenantId tenantId, @Param("agreementIds") List<UUID> agreementIds);
}
