package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvPriceLine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvPriceLineRepository extends JpaRepository<TrainProvPriceLine, UUID> {

    @Query("SELECT p FROM TrainProvPriceLine p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<TrainProvPriceLine> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT p FROM TrainProvPriceLine p WHERE p.tenantId = :#{#tenantId.value} AND p.programmeVersionId = :versionId
        AND (:status IS NULL OR p.status = :status)
        ORDER BY p.status, p.feeType, p.validFrom DESC
        """)
    List<TrainProvPriceLine> forVersion(TenantId tenantId, UUID versionId, String status);

    /** Live lines for the same funder (or no funder) and fee type. The service narrows by description for the OTHER type and by dates. */
    @Query("""
        SELECT p FROM TrainProvPriceLine p WHERE p.tenantId = :#{#tenantId.value} AND p.programmeVersionId = :versionId AND p.status = 'ACTIVE' AND p.feeType = :feeType
        AND ((CAST(:funderId AS uuid) IS NULL AND p.funderId IS NULL) OR p.funderId = :funderId)
        """)
    List<TrainProvPriceLine> liveForSameFee(TenantId tenantId, UUID versionId, UUID funderId, String feeType);

    @Query("""
        SELECT p FROM TrainProvPriceLine p WHERE p.tenantId = :#{#tenantId.value} AND p.programmeVersionId = :versionId AND p.status = 'ACTIVE'
        AND p.validFrom <= :on AND (p.validTo IS NULL OR p.validTo >= :on) ORDER BY p.feeType, p.validFrom DESC
        """)
    List<TrainProvPriceLine> inForce(TenantId tenantId, UUID versionId, LocalDate on);

    /** Waits for any other change to the same programme version's price book, until this transaction ends. */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtextextended(:key, 0)) AS text)", nativeQuery = true)
    String lock(String key);
}
