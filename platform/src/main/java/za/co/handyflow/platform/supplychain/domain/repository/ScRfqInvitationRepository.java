package za.co.handyflow.platform.supplychain.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScRfqInvitation;

public interface ScRfqInvitationRepository extends JpaRepository<ScRfqInvitation, UUID> {

    @Query("SELECT i FROM ScRfqInvitation i WHERE i.tenantId = :tenantId AND i.rfqId = :rfqId ORDER BY i.supplierName")
    List<ScRfqInvitation> findByRfq(@Param("tenantId") UUID tenantId, @Param("rfqId") UUID rfqId);

    @Query("SELECT i FROM ScRfqInvitation i WHERE i.tenantId = :tenantId AND i.rfqId = :rfqId AND i.supplierId = :supplierId")
    Optional<ScRfqInvitation> findByRfqAndSupplier(@Param("tenantId") UUID tenantId, @Param("rfqId") UUID rfqId,
                                                    @Param("supplierId") UUID supplierId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ScRfqInvitation i WHERE i.tenantId = :tenantId AND i.rfqId = :rfqId")
    void deleteByRfq(@Param("tenantId") UUID tenantId, @Param("rfqId") UUID rfqId);
}
