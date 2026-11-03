package za.co.handyflow.platform.documents.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.documents.domain.model.RetentionPolicy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

    @Query("SELECT p FROM RetentionPolicy p WHERE p.tenantId = :tenantId ORDER BY p.moduleCode, p.category, p.jurisdiction")
    List<RetentionPolicy> findForTenant(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT p FROM RetentionPolicy p
            WHERE p.tenantId = :tenantId AND p.moduleCode = :module AND p.category = :category AND p.jurisdiction = :jurisdiction
            """)
    Optional<RetentionPolicy> findOne(@Param("tenantId") UUID tenantId, @Param("module") String module,
                                      @Param("category") String category, @Param("jurisdiction") String jurisdiction);

    @Query("SELECT p FROM RetentionPolicy p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<RetentionPolicy> findOwned(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
