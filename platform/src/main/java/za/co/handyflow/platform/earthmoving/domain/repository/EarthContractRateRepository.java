package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthContractRate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthContractRateRepository extends JpaRepository<EarthContractRate, UUID> {

    @Query("SELECT r FROM EarthContractRate r WHERE r.tenantId = :tenantId AND r.contractId = :contractId ORDER BY r.basis")
    List<EarthContractRate> findByContract(TenantId tenantId, UUID contractId);

    @Query("SELECT r FROM EarthContractRate r WHERE r.tenantId = :tenantId AND r.contractId = :contractId AND r.basis = :basis")
    Optional<EarthContractRate> findByContractAndBasis(TenantId tenantId, UUID contractId, String basis);
}
