package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderSubmissionSnapshot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenderSubmissionSnapshotRepository extends JpaRepository<TenderSubmissionSnapshot, UUID> {

    @Query("SELECT s FROM TenderSubmissionSnapshot s WHERE s.tenantId.value = :#{#tenantId.value} " +
           "AND s.tenderId = :tenderId ORDER BY s.snapshotNumber DESC")
    List<TenderSubmissionSnapshot> findByTender(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);

    @Query("SELECT COUNT(s) FROM TenderSubmissionSnapshot s WHERE s.tenantId.value = :#{#tenantId.value} AND s.tenderId = :tenderId")
    long countByTender(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);

    @Query("SELECT s FROM TenderSubmissionSnapshot s WHERE s.tenantId.value = :#{#tenantId.value} AND s.id = :id")
    Optional<TenderSubmissionSnapshot> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
