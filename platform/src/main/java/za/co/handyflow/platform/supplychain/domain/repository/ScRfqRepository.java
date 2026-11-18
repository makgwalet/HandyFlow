package za.co.handyflow.platform.supplychain.domain.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.RfqStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScRfq;

public interface ScRfqRepository extends JpaRepository<ScRfq, UUID> {

    @Query("SELECT r FROM ScRfq r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<ScRfq> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Row lock: serialises publish / close / award / quote submission on one RFQ. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ScRfq r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<ScRfq> lockByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query(value = "SELECT r FROM ScRfq r WHERE r.tenantId = :tenantId AND r.status = :status ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM ScRfq r WHERE r.tenantId = :tenantId AND r.status = :status")
    Page<ScRfq> findByStatus(@Param("tenantId") UUID tenantId, @Param("status") RfqStatus status, Pageable pageable);

    @Query(value = "SELECT r FROM ScRfq r WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM ScRfq r WHERE r.tenantId = :tenantId")
    Page<ScRfq> findAllForTenant(@Param("tenantId") UUID tenantId, Pageable pageable);
}
