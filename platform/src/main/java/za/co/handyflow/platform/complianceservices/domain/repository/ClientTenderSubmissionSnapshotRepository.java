package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderSubmissionSnapshot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientTenderSubmissionSnapshotRepository extends JpaRepository<ClientTenderSubmissionSnapshot, UUID> {

    @Query("SELECT s FROM ClientTenderSubmissionSnapshot s WHERE s.tenantId = :#{#tenantId.value} " +
           "AND s.clientTenderId = :clientTenderId ORDER BY s.snapshotNumber DESC")
    List<ClientTenderSubmissionSnapshot> findByTender(@Param("tenantId") TenantId tenantId, @Param("clientTenderId") UUID clientTenderId);

    @Query("SELECT COUNT(s) FROM ClientTenderSubmissionSnapshot s WHERE s.tenantId = :#{#tenantId.value} AND s.clientTenderId = :clientTenderId")
    long countByTender(@Param("tenantId") TenantId tenantId, @Param("clientTenderId") UUID clientTenderId);

    @Query("SELECT s FROM ClientTenderSubmissionSnapshot s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id")
    Optional<ClientTenderSubmissionSnapshot> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
