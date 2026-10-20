package za.co.handyflow.platform.documents.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.documents.domain.model.DocumentVersion;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    @Query("SELECT v FROM DocumentVersion v WHERE v.tenantId = :tenantId AND v.documentId = :documentId ORDER BY v.versionNo DESC")
    List<DocumentVersion> findForDocument(TenantId tenantId, UUID documentId);

    @Query("SELECT v FROM DocumentVersion v WHERE v.tenantId = :tenantId AND v.documentId = :documentId AND v.versionNo = :versionNo")
    Optional<DocumentVersion> findOne(TenantId tenantId, UUID documentId, int versionNo);

    @Query("SELECT v FROM DocumentVersion v WHERE v.tenantId = :tenantId AND v.documentId IN :documentIds")
    List<DocumentVersion> findForDocuments(TenantId tenantId, Collection<UUID> documentIds);
}
