package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatterParty;

public interface LpMatterPartyRepository extends JpaRepository<LpMatterParty, UUID> {

    @Query("SELECT p FROM LpMatterParty p WHERE p.tenantId = :tenantId AND p.matterId = :matterId ORDER BY p.name")
    List<LpMatterParty> findByMatter(TenantId tenantId, UUID matterId);

    /** Candidate rows whose name contains a word (lower case, letters and digits only). The caller does the real comparison. */
    @Query("SELECT p FROM LpMatterParty p WHERE p.tenantId = :tenantId AND lower(p.name) LIKE :pattern ORDER BY p.addedAt DESC")
    List<LpMatterParty> candidatesByName(TenantId tenantId, String pattern, Pageable limit);

    @Query("""
        SELECT p FROM LpMatterParty p WHERE p.tenantId = :tenantId AND p.idNumber IS NOT NULL
        AND lower(replace(replace(replace(p.idNumber, ' ', ''), '-', ''), '/', '')) = :idNumber
        ORDER BY p.addedAt DESC
        """)
    List<LpMatterParty> byIdNumber(TenantId tenantId, String idNumber, Pageable limit);
}
