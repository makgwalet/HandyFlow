package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccClient;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccClientRepository extends JpaRepository<AccClient, UUID> {

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.deletedAt IS NULL
        ORDER BY c.tradingName ASC
    """)
    Page<AccClient> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.id = :id
          AND c.deletedAt IS NULL
    """)
    Optional<AccClient> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.riskRating = :risk
          AND c.deletedAt IS NULL
        ORDER BY c.tradingName ASC
    """)
    List<AccClient> findByRisk(@Param("tenantId") TenantId tenantId, @Param("risk") String risk);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.ficaCompleted = false
          AND c.deletedAt IS NULL
        ORDER BY c.tradingName ASC
    """)
    List<AccClient> findFicaIncomplete(@Param("tenantId") TenantId tenantId);

    @Query("""
        SELECT c FROM AccountantClient c
        WHERE c.tenantId = :tenantId
          AND c.tcsPinExpiry IS NOT NULL
          AND c.tcsPinExpiry < :cutoff
          AND c.deletedAt IS NULL
    """)
    List<AccClient> findWithExpiredTcsPin(@Param("tenantId") TenantId tenantId,
                                          @Param("cutoff") LocalDate cutoff);
}
