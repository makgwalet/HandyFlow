package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.contracting.domain.model.ContractAuthorityGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractAuthorityGrantRepository extends JpaRepository<ContractAuthorityGrant, UUID> {
    @Query("SELECT g FROM ContractAuthorityGrant g WHERE g.tenantId = :tenantId ORDER BY g.createdAt DESC")
    List<ContractAuthorityGrant> findByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT g FROM ContractAuthorityGrant g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<ContractAuthorityGrant> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
