package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.PostOrderAcknowledgement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostOrderAcknowledgementRepository extends JpaRepository<PostOrderAcknowledgement, UUID> {

    // Backs "has this guard already acknowledged THIS exact version" —
    // the check that decides whether the guard app shows the order as
    // already-read or still needing acknowledgment.
    @Query("SELECT a FROM PostOrderAcknowledgement a WHERE a.tenantId = :tenantId AND a.postOrderId = :postOrderId AND a.version = :version AND a.guardId = :guardId")
    Optional<PostOrderAcknowledgement> findByOrderVersionAndGuard(TenantId tenantId, UUID postOrderId, int version, UUID guardId);

    @Query("SELECT a FROM PostOrderAcknowledgement a WHERE a.tenantId = :tenantId AND a.postOrderId = :postOrderId ORDER BY a.acknowledgedAt DESC")
    List<PostOrderAcknowledgement> findByPostOrder(TenantId tenantId, UUID postOrderId);

    @Query("SELECT a FROM PostOrderAcknowledgement a WHERE a.tenantId = :tenantId AND a.guardId = :guardId ORDER BY a.acknowledgedAt DESC")
    List<PostOrderAcknowledgement> findByGuard(TenantId tenantId, UUID guardId);
}
