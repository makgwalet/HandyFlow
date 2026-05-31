// security/domain/repository/SiteRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    @Query("SELECT s FROM Site s WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL ORDER BY s.name")
    Page<Site> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT s FROM Site s LEFT JOIN FETCH s.checkpoints WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<Site> findActiveByIdWithCheckpoints(TenantId tenantId, UUID id);

    @Query("SELECT s FROM Site s WHERE s.tenantId = :tenantId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<Site> findActiveById(TenantId tenantId, UUID id);
}