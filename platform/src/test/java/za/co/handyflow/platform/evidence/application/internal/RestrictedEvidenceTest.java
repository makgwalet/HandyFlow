package za.co.handyflow.platform.evidence.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.domain.model.Evidence;
import za.co.handyflow.platform.evidence.domain.repository.EvidenceRepository;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Restricted evidence can only be reached through its own module's methods. */
class RestrictedEvidenceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final String SOURCE = "restricted-hr-er";

    private final EvidenceRepository repo = mock(EvidenceRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final AuditTrailFacade trail = mock(AuditTrailFacade.class);
    private final EvidenceService service = new EvidenceService(repo, storage, trail);

    private Evidence restrictedFile(UUID entity) {
        Evidence e = Evidence.attach(TENANT.getValue(), "grievance-j-smith.pdf", "application/pdf", 6, "k1", "STATEMENT", SOURCE, "ER_CASE", entity, null, "h", UUID.randomUUID(), "HR");
        when(repo.findByTenantAndId(TENANT.getValue(), e.getId())).thenReturn(Optional.of(e));
        try { when(storage.retrieve("k1")).thenReturn(new byte[]{1}); } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        return e;
    }

    @Test
    void theGeneralMethodsRefuseToAttachOrListRestrictedEvidence() {
        MockMultipartFile f = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> service.attach(TENANT, f, "STATEMENT", SOURCE, "ER_CASE", UUID.randomUUID(), null, UUID.randomUUID(), "x"))
                .isInstanceOf(HandyFlowException.class);
        assertThat(service.listFor(TENANT, SOURCE, "ER_CASE", UUID.randomUUID())).isEmpty();
        verifyNoInteractions(storage);
        verify(repo, never()).findActiveForEntity(any(), anyString(), anyString(), any());
    }

    @Test
    void theGeneralDownloadAndDetachCannotFindARestrictedFile() {
        Evidence e = restrictedFile(UUID.randomUUID());
        assertThatThrownBy(() -> service.download(TENANT, e.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.detach(TENANT, e.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(e.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void theRestrictedMethodsNeedARestrictedModuleName() {
        assertThatThrownBy(() -> service.listRestricted(TENANT, "hr", "X", UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.downloadRestricted(TENANT, "hr", "X", UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRestrictedFileOpensOnlyThroughTheEntityItIsAttachedTo() {
        UUID entity = UUID.randomUUID();
        Evidence e = restrictedFile(entity);
        assertThat(service.downloadRestricted(TENANT, SOURCE, "ER_CASE", entity, e.getId()).fileName()).isEqualTo("grievance-j-smith.pdf");
        assertThatThrownBy(() -> service.downloadRestricted(TENANT, SOURCE, "ER_CASE", UUID.randomUUID(), e.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.downloadRestricted(TENANT, SOURCE, "OTHER", entity, e.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.downloadRestricted(TENANT, "restricted-other", "ER_CASE", entity, e.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theGeneralAuditTrailNeverCarriesARestrictedFileName() {
        UUID entity = UUID.randomUUID();
        Evidence e = restrictedFile(entity);
        service.downloadRestricted(TENANT, SOURCE, "ER_CASE", entity, e.getId());
        verify(trail).record(eq(TENANT), eq("EVIDENCE_DOWNLOADED"), eq("EVIDENCE"), eq(e.getId()), any(), eq(Map.of("sourceModule", SOURCE)));
    }

    @Test
    void detachingARestrictedFileHidesItAndKeepsTheStoredFile() throws Exception {
        UUID entity = UUID.randomUUID();
        Evidence e = restrictedFile(entity);
        service.detachRestricted(TENANT, SOURCE, "ER_CASE", entity, e.getId());
        assertThat(e.getStatus()).isEqualTo("DETACHED");
        verify(storage, never()).delete(anyString());
        assertThatThrownBy(() -> service.detachRestricted(TENANT, SOURCE, "ER_CASE", entity, e.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void tenantWideListingAndAuditorSharingGoThroughQueriesThatLeaveRestrictedFilesOut() {
        when(repo.findAllActiveForTenant(TENANT.getValue())).thenReturn(List.of());
        assertThat(service.listAllForTenant(TENANT)).isEmpty();
        assertThat(EvidenceFacade.isRestricted(SOURCE)).isTrue();
        assertThat(EvidenceFacade.isRestricted("expenses")).isFalse();
        assertThat(EvidenceFacade.isRestricted(null)).isFalse();
    }
}
