// security/domain/repository/ProtectionDetailRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.ProtectionDetail;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProtectionDetailRepository extends JpaRepository<ProtectionDetail, UUID> {

    @Query("""
        SELECT d FROM ProtectionDetail d
        WHERE d.tenantId = :tenantId
        AND d.id = :id
        """)
    Optional<ProtectionDetail> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT d FROM ProtectionDetail d
        WHERE d.tenantId = :tenantId
        AND d.principalId = :principalId
        ORDER BY d.startAt DESC
        """)
    List<ProtectionDetail> findByPrincipal(TenantId tenantId, UUID principalId);

    @Query("""
        SELECT d FROM ProtectionDetail d
        WHERE d.tenantId = :tenantId
        AND d.status IN ('PLANNED', 'ACTIVE')
        ORDER BY d.startAt
        """)
    Page<ProtectionDetail> findActiveOrPlanned(TenantId tenantId, Pageable pageable);
}
