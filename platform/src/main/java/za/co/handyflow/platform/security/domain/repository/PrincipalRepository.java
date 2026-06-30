// security/domain/repository/PrincipalRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Principal;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface PrincipalRepository extends JpaRepository<Principal, UUID> {

    @Query("""
        SELECT p FROM Principal p
        WHERE p.tenantId = :tenantId
        AND p.id = :id
        """)
    Optional<Principal> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT p FROM Principal p
        WHERE p.tenantId = :tenantId
        AND p.active = true
        ORDER BY p.aliasCodename
        """)
    Page<Principal> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT COUNT(p) > 0 FROM Principal p
        WHERE p.tenantId = :tenantId
        AND p.aliasCodename = :codename
        """)
    boolean existsByCodename(TenantId tenantId, String codename);
}
