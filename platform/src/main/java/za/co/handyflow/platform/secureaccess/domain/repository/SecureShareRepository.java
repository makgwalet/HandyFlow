package za.co.handyflow.platform.secureaccess.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.secureaccess.domain.model.SecureShare;

import java.util.Optional;
import java.util.UUID;

public interface SecureShareRepository extends JpaRepository<SecureShare, UUID> {

    @Query("SELECT s FROM SecureShare s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<SecureShare> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
            SELECT s FROM SecureShare s
            WHERE s.tenantId = :tenantId AND s.moduleCode = :moduleCode
              AND s.sourceType = :sourceType AND s.sourceId = :sourceId
            """)
    Optional<SecureShare> findBySource(@Param("tenantId") UUID tenantId, @Param("moduleCode") String moduleCode,
                                       @Param("sourceType") String sourceType, @Param("sourceId") UUID sourceId);
}
