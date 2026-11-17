package za.co.handyflow.platform.supplychain.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.RequisitionStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScRequisition;

import java.util.Optional;
import java.util.UUID;

public interface ScRequisitionRepository extends JpaRepository<ScRequisition, UUID> {

    @Query("SELECT r FROM ScRequisition r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<ScRequisition> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Locked for approve / convert / cancel so two buyers can't over-convert the same quantity. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ScRequisition r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<ScRequisition> lockByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    // Four explicit finders instead of "(:p IS NULL OR ...)" — null UUID/enum parameters break on PostgreSQL.
    @Query("SELECT r FROM ScRequisition r WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC")
    Page<ScRequisition> findAllForTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT r FROM ScRequisition r WHERE r.tenantId = :tenantId AND r.status = :status ORDER BY r.createdAt DESC")
    Page<ScRequisition> findByStatus(@Param("tenantId") UUID tenantId, @Param("status") RequisitionStatus status, Pageable pageable);

    @Query("SELECT r FROM ScRequisition r WHERE r.tenantId = :tenantId AND r.requestedBy = :userId ORDER BY r.createdAt DESC")
    Page<ScRequisition> findByRequester(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT r FROM ScRequisition r WHERE r.tenantId = :tenantId AND r.requestedBy = :userId AND r.status = :status ORDER BY r.createdAt DESC")
    Page<ScRequisition> findByRequesterAndStatus(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                                                 @Param("status") RequisitionStatus status, Pageable pageable);
}
