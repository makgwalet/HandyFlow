package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollAgencyDebtorAccountRepository extends JpaRepository<CollAgencyDebtorAccount, UUID> {

    @Query("""
        SELECT a FROM CollAgencyDebtorAccount a WHERE a.tenantId = :tenantId AND a.clientId = :clientId
        AND a.deletedAt IS NULL AND (:status IS NULL OR a.status = :status)
        ORDER BY a.placedDate DESC
        """)
    Page<CollAgencyDebtorAccount> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId,
                                               @Param("status") String status, Pageable pageable);

    @Query("SELECT a FROM CollAgencyDebtorAccount a WHERE a.tenantId = :tenantId AND a.id = :id AND a.deletedAt IS NULL")
    Optional<CollAgencyDebtorAccount> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM CollAgencyDebtorAccount a WHERE a.tenantId = :tenantId AND a.placementBatchId = :batchId AND a.deletedAt IS NULL")
    List<CollAgencyDebtorAccount> findByPlacementBatch(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId);

    /** Unpaginated — used by the client portfolio/recovery report and PDF export. */
    @Query("SELECT a FROM CollAgencyDebtorAccount a WHERE a.tenantId = :tenantId AND a.clientId = :clientId AND a.deletedAt IS NULL ORDER BY a.placedDate DESC")
    List<CollAgencyDebtorAccount> findAllActiveForClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);
}
