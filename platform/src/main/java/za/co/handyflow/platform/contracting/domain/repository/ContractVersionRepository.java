package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractVersionRepository extends JpaRepository<ContractVersion, UUID> {

    List<ContractVersion> findByTenantIdAndContractIdOrderByVersionNoDesc(UUID tenantId, UUID contractId);

    Optional<ContractVersion> findByTenantIdAndContractIdAndVersionNo(UUID tenantId, UUID contractId, int versionNo);

    Optional<ContractVersion> findFirstByTenantIdAndContractIdOrderByVersionNoDesc(UUID tenantId, UUID contractId);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM ContractVersion v WHERE v.tenantId = :tenantId AND v.contractId = :contractId")
    int latestVersionNo(UUID tenantId, UUID contractId);
}
