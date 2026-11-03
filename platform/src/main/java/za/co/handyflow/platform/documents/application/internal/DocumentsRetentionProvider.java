package za.co.handyflow.platform.documents.application.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.DocumentStore;
import za.co.handyflow.platform.documents.application.RetentionProvider;
import za.co.handyflow.platform.documents.domain.model.Document;
import za.co.handyflow.platform.documents.domain.model.DocumentVersion;
import za.co.handyflow.platform.documents.domain.repository.DocumentRepository;
import za.co.handyflow.platform.documents.domain.repository.DocumentVersionRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document Service records (module DOCUMENTS, source type DOCUMENT). The clock starts when a document is archived. Disposal deletes
 * the files of every version through the document store (which keeps its own tombstone per file) and marks the document disposed;
 * the document and its version rows stay.
 */
@Component
class DocumentsRetentionProvider implements RetentionProvider {

    static final String MODULE = "DOCUMENTS";
    static final String SOURCE = "DOCUMENT";

    private static final Logger log = LoggerFactory.getLogger(DocumentsRetentionProvider.class);

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final DocumentStore store;
    private final AuditTrailFacade trail;

    DocumentsRetentionProvider(DocumentRepository documents, DocumentVersionRepository versions, DocumentStore store, AuditTrailFacade trail) {
        this.documents = documents;
        this.versions = versions;
        this.store = store;
        this.trail = trail;
    }

    @Override
    public String moduleCode() { return MODULE; }

    @Override
    public Disposed dispose(TenantId tenantId, String sourceType, UUID sourceId, UUID batchId, UUID approvedBy) {
        if (!SOURCE.equals(sourceType)) throw new IllegalArgumentException("Unknown Document Service record type " + sourceType);
        Document d = documents.findOwned(tenantId, sourceId).orElseThrow(() -> new IllegalStateException("The document no longer exists"));
        if (d.isDisposed()) throw new IllegalStateException("The document was already disposed of");
        if (d.isActive()) throw new IllegalStateException("The document is in use again; it was not disposed of");
        List<DocumentVersion> all = versions.findForDocument(tenantId, d.getId());
        StringBuilder hashes = new StringBuilder();
        for (DocumentVersion v : all) {
            try { store.delete(tenantId, v.getStorageKey(), "Disposed under the retention policy (request " + batchId + ")"); }
            catch (IOException e) { throw new IllegalStateException("A file could not be removed from storage: " + e.getMessage()); }
            if (hashes.length() < 700) hashes.append(hashes.length() == 0 ? "" : ", ").append("v").append(v.getVersionNo()).append(' ').append(v.getSha256(), 0, 12);
        }
        d.markDisposed(batchId);
        documents.save(d);
        trail.record(tenantId, "DOCUMENT_DISPOSED", "DOCUMENT", d.getId(), Map.of("status", d.getStatus()),
                Map.of("files", all.size(), "batch", batchId.toString(), "title", d.getTitle()));
        log.info("Document {} disposed: {} file(s) removed (request {})", d.getId(), all.size(), batchId);
        return new Disposed(all.size(), all.size() + " file(s) removed" + (hashes.length() > 0 ? "; sha256 " + hashes : ""));
    }
}
