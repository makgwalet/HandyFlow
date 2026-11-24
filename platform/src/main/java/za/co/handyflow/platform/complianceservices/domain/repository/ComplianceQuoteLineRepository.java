package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceQuoteLine;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ComplianceQuoteLineRepository extends JpaRepository<ComplianceQuoteLine, UUID> {

    @Query("SELECT l FROM ComplianceQuoteLine l WHERE l.tenantId.value = :#{#tenantId.value} AND l.quoteId IN :quoteIds ORDER BY l.quoteId, l.sortOrder")
    List<ComplianceQuoteLine> findForQuotes(@Param("tenantId") TenantId tenantId, @Param("quoteIds") List<UUID> quoteIds);

    @Modifying
    @Query("DELETE FROM ComplianceQuoteLine l WHERE l.quoteId = :quoteId")
    void deleteForQuote(@Param("quoteId") UUID quoteId);
}
