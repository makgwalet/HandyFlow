package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpRetainerAgreement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpRetainerAgreementRepository extends JpaRepository<LpRetainerAgreement, UUID> {

    @Query("SELECT r FROM LpRetainerAgreement r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<LpRetainerAgreement> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT r FROM LpRetainerAgreement r
        WHERE r.tenantId = :tenantId AND r.clientId = :clientId
        ORDER BY r.startDate DESC
        """)
    List<LpRetainerAgreement> findAllForClient(TenantId tenantId, UUID clientId);
}
