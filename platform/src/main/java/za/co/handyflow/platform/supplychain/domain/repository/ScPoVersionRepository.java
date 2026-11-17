package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScPoVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScPoVersionRepository extends JpaRepository<ScPoVersion, UUID> {

    @Query("SELECT v FROM ScPoVersion v WHERE v.tenantId = :tenantId AND v.purchaseOrderId = :poId ORDER BY v.revisionNumber DESC")
    List<ScPoVersion> findAllForPo(@Param("tenantId") UUID tenantId, @Param("poId") UUID poId);

    @Query("SELECT v FROM ScPoVersion v WHERE v.tenantId = :tenantId AND v.purchaseOrderId = :poId AND v.revisionNumber = :rev")
    Optional<ScPoVersion> findByPoAndRevision(@Param("tenantId") UUID tenantId, @Param("poId") UUID poId, @Param("rev") int revision);
}
