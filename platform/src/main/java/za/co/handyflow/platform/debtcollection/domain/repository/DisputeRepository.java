package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.Dispute;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    @Query("SELECT d FROM Dispute d WHERE d.tenantId = :tenantId AND d.caseId = :caseId ORDER BY d.createdAt DESC")
    List<Dispute> findByCaseId(TenantId tenantId, UUID caseId);

    @Query("SELECT d FROM Dispute d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<Dispute> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT d FROM Dispute d WHERE d.tenantId = :tenantId AND d.status <> 'RESOLVED' ORDER BY d.responseDue ASC")
    List<Dispute> findUnresolved(TenantId tenantId);

    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.tenantId = :tenantId AND d.caseId = :caseId AND d.status <> 'RESOLVED' AND d.id <> :exceptId")
    long countOtherUnresolved(TenantId tenantId, UUID caseId, UUID exceptId);
}
