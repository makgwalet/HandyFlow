// security/domain/repository/PrincipalVettingRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.PrincipalVetting;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrincipalVettingRepository extends JpaRepository<PrincipalVetting, UUID> {

    @Query("""
        SELECT v FROM PrincipalVetting v
        WHERE v.tenantId = :tenantId AND v.id = :id
        """)
    Optional<PrincipalVetting> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT v FROM PrincipalVetting v
        WHERE v.tenantId = :tenantId AND v.principalId = :principalId
        ORDER BY v.createdAt DESC
        """)
    List<PrincipalVetting> findByPrincipal(TenantId tenantId, UUID principalId);

    @Query("""
        SELECT COUNT(v) > 0 FROM PrincipalVetting v
        WHERE v.principalId = :principalId AND v.result = 'HIT'
        """)
    boolean hasHit(UUID principalId);

    @Query("""
        SELECT COUNT(v) > 0 FROM PrincipalVetting v
        WHERE v.principalId = :principalId AND v.result = 'PENDING'
        """)
    boolean hasPending(UUID principalId);
}
