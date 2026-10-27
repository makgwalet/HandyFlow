package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuoteLine;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FmQuoteLineRepository extends JpaRepository<FmQuoteLine, UUID> {

    @Query("SELECT l FROM FmQuoteLine l WHERE l.tenantId.value = :#{#tenantId.value} AND l.quoteId = :quoteId ORDER BY l.lineNo ASC")
    List<FmQuoteLine> findForQuote(@Param("tenantId") TenantId tenantId, @Param("quoteId") UUID quoteId);

    @Query("SELECT l FROM FmQuoteLine l WHERE l.tenantId.value = :#{#tenantId.value} AND l.quoteId IN :quoteIds ORDER BY l.quoteId, l.lineNo ASC")
    List<FmQuoteLine> findForQuotes(@Param("tenantId") TenantId tenantId, @Param("quoteIds") Collection<UUID> quoteIds);

    @Modifying
    @Query("DELETE FROM FmQuoteLine l WHERE l.tenantId.value = :#{#tenantId.value} AND l.quoteId = :quoteId")
    void deleteForQuote(@Param("tenantId") TenantId tenantId, @Param("quoteId") UUID quoteId);
}
