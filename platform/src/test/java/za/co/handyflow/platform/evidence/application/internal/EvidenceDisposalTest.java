package za.co.handyflow.platform.evidence.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.DocumentStore;
import za.co.handyflow.platform.evidence.domain.model.Evidence;
import za.co.handyflow.platform.evidence.domain.repository.EvidenceRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Disposal of a piece of evidence under an approved records-retention disposal. */
class EvidenceDisposalTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));

    private final EvidenceRepository repo = mock(EvidenceRepository.class);
    private final DocumentStore store = mock(DocumentStore.class);
    private final AuditTrailFacade trail = mock(AuditTrailFacade.class);
    private final EvidenceService service = new EvidenceService(repo, store, trail);

    private Evidence projectFile(UUID doc) {
        Evidence e = Evidence.attach(TENANT.getValue(), "DRG-001.pdf", "application/pdf", 6, "evidence/projects/k1", "PROJECT_DOCUMENT",
                "projects", "ProjectDocument", doc, null, "ab".repeat(32), UUID.randomUUID(), "PM");
        when(repo.findByTenantAndId(TENANT.getValue(), e.getId())).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void theFileIsDeletedAndTheRowKeptWithItsHash() throws Exception {
        UUID doc = UUID.randomUUID();
        Evidence e = projectFile(doc);
        String sha = service.dispose(TENANT, "projects", "ProjectDocument", doc, e.getId(), "Disposed under the retention policy (request x)");
        assertThat(sha).isEqualTo("ab".repeat(32));
        assertThat(e.getStatus()).isEqualTo("DISPOSED");
        verify(store).delete(eq(TENANT), eq("evidence/projects/k1"), anyString());
        verify(repo).save(e);
        verify(trail).record(eq(TENANT), eq("EVIDENCE_DISPOSED"), eq("EVIDENCE"), eq(e.getId()), anyMap(), anyMap());
    }

    @Test
    void aDisposedFileCannotBeDownloaded() throws Exception {
        UUID doc = UUID.randomUUID();
        Evidence e = projectFile(doc);
        service.dispose(TENANT, "projects", "ProjectDocument", doc, e.getId(), null);
        assertThatThrownBy(() -> service.download(TENANT, e.getId()))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(x -> assertThat(((HandyFlowException) x).getErrorCode()).isEqualTo("DISPOSED"));
        verify(store, never()).get(any(), any());
    }

    @Test
    void onlyTheRecordTheFileBelongsToCanDisposeOfIt() throws Exception {
        UUID doc = UUID.randomUUID();
        Evidence e = projectFile(doc);
        assertThatThrownBy(() -> service.dispose(TENANT, "projects", "ProjectDocument", UUID.randomUUID(), e.getId(), null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.dispose(TENANT, "expenses", "ProjectDocument", doc, e.getId(), null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(e.getStatus()).isEqualTo("ACTIVE");
        verify(store, never()).delete(any(), anyString(), any());
    }

    @Test
    void disposingTwiceDoesNotTouchStorageAgain() throws Exception {
        UUID doc = UUID.randomUUID();
        Evidence e = projectFile(doc);
        service.dispose(TENANT, "projects", "ProjectDocument", doc, e.getId(), null);
        service.dispose(TENANT, "projects", "ProjectDocument", doc, e.getId(), null);
        verify(store, times(1)).delete(any(), anyString(), any());
    }

    @Test
    void aStorageFailureLeavesTheEvidenceActive() throws Exception {
        UUID doc = UUID.randomUUID();
        Evidence e = projectFile(doc);
        doThrow(new java.io.IOException("down")).when(store).delete(any(), anyString(), any());
        assertThatThrownBy(() -> service.dispose(TENANT, "projects", "ProjectDocument", doc, e.getId(), null))
                .isInstanceOf(HandyFlowException.class);
        assertThat(e.getStatus()).isEqualTo("ACTIVE");
        verify(repo, never()).save(any());
    }
}
