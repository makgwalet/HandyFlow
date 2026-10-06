package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderSubmissionProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenderSubmissionProfileRepository extends JpaRepository<TenderSubmissionProfile, UUID> {

    @Query("SELECT p FROM TenderSubmissionProfile p WHERE p.tenantId.value = :#{#tenantId.value} ORDER BY LOWER(p.name)")
    List<TenderSubmissionProfile> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT p FROM TenderSubmissionProfile p WHERE p.tenantId.value = :#{#tenantId.value} AND p.id = :id")
    Optional<TenderSubmissionProfile> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT COUNT(p) > 0 FROM TenderSubmissionProfile p WHERE p.tenantId.value = :#{#tenantId.value} AND LOWER(p.name) = LOWER(:name) AND (:excludeId IS NULL OR p.id <> :excludeId)")
    boolean nameTaken(@Param("tenantId") TenantId tenantId, @Param("name") String name, @Param("excludeId") UUID excludeId);
}
