// security/domain/repository/DeclinedPrincipalRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.DeclinedPrincipal;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeclinedPrincipalRepository extends JpaRepository<DeclinedPrincipal, UUID> {

    @Query("""
        SELECT d FROM DeclinedPrincipal d
        WHERE d.tenantId = :tenantId ORDER BY d.declinedAt DESC
        """)
    List<DeclinedPrincipal> findAllByTenant(TenantId tenantId);

    Optional<DeclinedPrincipal> findByTenantIdAndPrincipalId(TenantId tenantId, UUID principalId);

    @Query("""
        SELECT COUNT(d) > 0 FROM DeclinedPrincipal d
        WHERE d.tenantId = :tenantId AND d.principalId = :principalId
        """)
    boolean isDeclined(TenantId tenantId, UUID principalId);
}
