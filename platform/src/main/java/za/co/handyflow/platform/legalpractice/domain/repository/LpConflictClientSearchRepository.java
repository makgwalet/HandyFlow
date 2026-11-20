package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.repository.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.legalpractice.domain.model.LpClient;

/** Read-only candidate search over the firm's clients, current and former, for conflict checks. */
public interface LpConflictClientSearchRepository extends Repository<LpClient, UUID> {

    @Query("SELECT c FROM LpClient c WHERE c.tenantId = :tenantId AND lower(c.name) LIKE :pattern ORDER BY c.name")
    List<LpClient> candidatesByName(TenantId tenantId, String pattern, Pageable limit);

    @Query("""
        SELECT c FROM LpClient c WHERE c.tenantId = :tenantId AND c.idOrRegistrationNumber IS NOT NULL
        AND lower(replace(replace(replace(c.idOrRegistrationNumber, ' ', ''), '-', ''), '/', '')) = :idNumber
        """)
    List<LpClient> byIdNumber(TenantId tenantId, String idNumber, Pageable limit);
}
