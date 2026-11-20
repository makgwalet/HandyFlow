package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpDocumentVersion;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpDocumentVersionRepository extends JpaRepository<LpDocumentVersion, UUID> {

    @Query("SELECT v FROM LpDocumentVersion v WHERE v.tenantId = :tenantId AND v.documentId = :documentId ORDER BY v.versionNo DESC")
    List<LpDocumentVersion> findForDocument(TenantId tenantId, UUID documentId);

    @Query("SELECT v FROM LpDocumentVersion v WHERE v.tenantId = :tenantId AND v.documentId = :documentId AND v.versionNo = :versionNo")
    Optional<LpDocumentVersion> findByNo(TenantId tenantId, UUID documentId, int versionNo);
}
