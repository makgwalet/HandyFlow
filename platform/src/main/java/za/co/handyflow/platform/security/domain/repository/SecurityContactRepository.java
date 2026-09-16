package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.SecurityContact;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityContactRepository extends JpaRepository<SecurityContact, UUID> {

    @Query("SELECT c FROM SecurityContact c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<SecurityContact> findByTenantAndId(TenantId tenantId, UUID id);

    // Tenant-wide contacts (siteId null, e.g. "Police") plus contacts
    // scoped to this specific site — a post order at Site X should be
    // able to reference either.
    @Query("SELECT c FROM SecurityContact c WHERE c.tenantId = :tenantId AND c.active = true AND (c.siteId IS NULL OR c.siteId = :siteId) ORDER BY c.role, c.name")
    List<SecurityContact> findAvailableForSite(TenantId tenantId, UUID siteId);

    @Query("SELECT c FROM SecurityContact c WHERE c.tenantId = :tenantId AND c.active = true ORDER BY c.role, c.name")
    List<SecurityContact> findAllActive(TenantId tenantId);
}
