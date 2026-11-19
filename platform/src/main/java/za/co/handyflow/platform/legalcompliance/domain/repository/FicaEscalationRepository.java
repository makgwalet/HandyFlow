package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FicaEscalationRepository extends JpaRepository<FicaEscalation, UUID> {

    @Query("SELECT x FROM FicaEscalation x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<FicaEscalation> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM FicaEscalation x WHERE x.tenantId = :tenantId AND (:status IS NULL OR x.status = :status) ORDER BY x.createdAt DESC")
    List<FicaEscalation> search(TenantId tenantId, EscalationStatus status);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
