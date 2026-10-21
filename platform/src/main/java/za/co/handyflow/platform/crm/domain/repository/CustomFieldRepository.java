package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.CustomField;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomFieldRepository extends JpaRepository<CustomField, UUID> {

    @Query("SELECT f FROM CustomField f WHERE f.tenantId = :tenantId ORDER BY f.sortOrder ASC, f.createdAt ASC")
    List<CustomField> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT f FROM CustomField f WHERE f.tenantId = :tenantId AND f.active = true ORDER BY f.sortOrder ASC, f.createdAt ASC")
    List<CustomField> findActiveForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT f FROM CustomField f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<CustomField> findByIdAndTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
