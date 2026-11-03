package za.co.handyflow.platform.secureaccess.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.secureaccess.domain.model.SecureAccessLog;

import java.util.List;
import java.util.UUID;

public interface SecureAccessLogRepository extends JpaRepository<SecureAccessLog, UUID> {

    @Query("""
            SELECT l FROM SecureAccessLog l
            WHERE l.tenantId = :tenantId AND l.shareId = :shareId
            ORDER BY l.occurredAt DESC
            """)
    List<SecureAccessLog> findByShare(@Param("tenantId") UUID tenantId, @Param("shareId") UUID shareId);
}
