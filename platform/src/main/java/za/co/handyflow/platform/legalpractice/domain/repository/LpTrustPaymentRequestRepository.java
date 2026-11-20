package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustPaymentRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpTrustPaymentRequestRepository extends JpaRepository<LpTrustPaymentRequest, UUID> {

    @Query("SELECT r FROM LpTrustPaymentRequest r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<LpTrustPaymentRequest> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT r FROM LpTrustPaymentRequest r WHERE r.tenantId = :tenantId AND r.idempotencyKey = :key")
    Optional<LpTrustPaymentRequest> findByIdempotencyKey(TenantId tenantId, String key);

    @Query("""
        SELECT r FROM LpTrustPaymentRequest r WHERE r.tenantId = :tenantId
        AND (:status IS NULL OR r.status = :status)
        ORDER BY r.requestedAt DESC
        """)
    Page<LpTrustPaymentRequest> search(TenantId tenantId, String status, Pageable pageable);

    /** As {@link #search}, leaving out requests on a matter or client the signed-in user is walled from. */
    @Query("""
        SELECT r FROM LpTrustPaymentRequest r WHERE r.tenantId = :tenantId
        AND (:status IS NULL OR r.status = :status)
        AND r.clientId NOT IN :barredClientIds AND (r.matterId IS NULL OR r.matterId NOT IN :barredMatterIds)
        ORDER BY r.requestedAt DESC
        """)
    Page<LpTrustPaymentRequest> searchExcluding(TenantId tenantId, String status, List<UUID> barredMatterIds, List<UUID> barredClientIds, Pageable pageable);
}
