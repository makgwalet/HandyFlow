package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAccreditationRepository extends JpaRepository<TrainProvAccreditation, UUID> {

    @Query("""
        SELECT a FROM TrainProvAccreditation a
        WHERE a.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR a.status = :status)
        AND (:authorityId IS NULL OR a.authorityId = :authorityId)
        ORDER BY a.createdAt DESC
        """)
    List<TrainProvAccreditation> findAllForTenant(TenantId tenantId, String status, UUID authorityId);

    @Query("SELECT a FROM TrainProvAccreditation a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvAccreditation> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(a) > 0 FROM TrainProvAccreditation a
        WHERE a.tenantId = :#{#tenantId.value} AND a.authorityId = :authorityId
        AND LOWER(a.accreditationNumber) = LOWER(:number)
        AND a.status NOT IN ('WITHDRAWN', 'REJECTED')
        AND (:excludeId IS NULL OR a.id <> :excludeId)
        """)
    boolean numberTaken(TenantId tenantId, UUID authorityId, String number, UUID excludeId);

    @Query("SELECT COUNT(a) FROM TrainProvAccreditation a WHERE a.tenantId = :#{#tenantId.value} AND a.authorityId = :authorityId AND a.status NOT IN ('WITHDRAWN', 'REJECTED', 'EXPIRED')")
    long countLiveForAuthority(TenantId tenantId, UUID authorityId);
}
