// security/domain/repository/GuardRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface GuardRepository extends JpaRepository<Guard, UUID> {

    @Query("SELECT g FROM Guard g WHERE g.tenantId = :tenantId AND g.deletedAt IS NULL ORDER BY g.lastName, g.firstName")
    Page<Guard> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT g FROM Guard g WHERE g.tenantId = :tenantId AND g.id = :id AND g.deletedAt IS NULL")
    Optional<Guard> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT g FROM Guard g WHERE g.tenantId = :tenantId AND g.deletedAt IS NULL AND (LOWER(g.firstName) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(g.lastName) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<Guard> searchActive(TenantId tenantId, String search, Pageable pageable);

    boolean existsByTenantIdAndPsiraNumberAndDeletedAtIsNull(TenantId tenantId, String psiraNumber);
}