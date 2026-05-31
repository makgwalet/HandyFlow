package za.co.handyflow.platform.marketing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.marketing.domain.model.MktContactPreference;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MktContactPreferenceRepository extends JpaRepository<MktContactPreference, UUID> {

    Optional<MktContactPreference> findByTenantIdAndEmail(TenantId tenantId, String email);

    Optional<MktContactPreference> findByUnsubscribeToken(String token);

    @Query("""
        SELECT p FROM MktContactPreference p
        WHERE p.tenantId = :tenantId
        AND p.emailOptedIn = true
        ORDER BY p.createdAt DESC
        """)
    List<MktContactPreference> findAllOptedIn(TenantId tenantId);

    @Query("""
        SELECT p FROM MktContactPreference p
        WHERE p.tenantId = :tenantId
        ORDER BY p.createdAt DESC
        """)
    Page<MktContactPreference> findAll(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT COUNT(p) FROM MktContactPreference p
        WHERE p.tenantId = :tenantId AND p.emailOptedIn = true
        """)
    long countOptedIn(TenantId tenantId);

    boolean existsByTenantIdAndEmail(TenantId tenantId, String email);
}
