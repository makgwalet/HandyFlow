package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.AccWorkpaperAudit;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccWorkpaperAuditRepository extends JpaRepository<AccWorkpaperAudit, UUID> {

    @Query("""
        SELECT a FROM AccountantWorkpaperAudit a
        WHERE a.tenantId = :tenantId AND a.fileId = :fileId
        ORDER BY a.performedAt DESC
    """)
    List<AccWorkpaperAudit> findByFile(@Param("tenantId") UUID tenantId, @Param("fileId") UUID fileId);
}