package za.co.handyflow.platform.recruitmentagency.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyRequisition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecAgencyRequisitionRepository extends JpaRepository<RecAgencyRequisition, UUID> {

    @Query("SELECT r FROM RecAgencyRequisition r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<RecAgencyRequisition> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM RecAgencyRequisition r WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC")
    Page<RecAgencyRequisition> findByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT r FROM RecAgencyRequisition r WHERE r.clientId = :clientId ORDER BY r.createdAt DESC")
    List<RecAgencyRequisition> findByClient(@Param("clientId") UUID clientId);
}