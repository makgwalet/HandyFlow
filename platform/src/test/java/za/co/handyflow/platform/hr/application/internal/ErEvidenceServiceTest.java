package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.hr.domain.model.ErEvidenceRules;
import za.co.handyflow.platform.hr.domain.model.ErRules;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.domain.model.HrErEvent;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.hr.application.internal.ErHarness.*;

/** Files on an employee relations case: who may add and open them, what is accepted, and what the case timeline records. */
class ErEvidenceServiceTest {

    private final ErHarness h = new ErHarness();
    private final EvidenceFacade facade = mock(EvidenceFacade.class);
    private final ErEvidenceService service = new ErEvidenceService(h.support, facade);
    private final UUID emp = h.employee();
    private final HrErCase theCase = h.discipline(emp);

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "text/html", new byte[]{'%', 'P', 'D', 'F', '-', '1'});
    }

    private static EvidenceResponse stored(UUID id, String name) {
        return new EvidenceResponse(id, name, "application/pdf", 6, "STATEMENT", "ACTIVE", "HR", Instant.now());
    }

    @Test
    void aFileIsStoredAsRestrictedEvidenceWithTheTypeFromItsExtensionAndNotedOnTheTimeline() {
        when(facade.listRestricted(any(), anyString(), anyString(), any())).thenReturn(List.of());
        when(facade.attachRestricted(any(), any(), anyString(), anyString(), anyString(), any(), any(), any())).thenReturn(stored(UUID.randomUUID(), "witness.pdf"));

        service.add(TENANT, theCase.getId(), "STATEMENT", pdf("..\\witness.pdf"), MANAGE, HR_USER, "Hanna");

        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(facade).attachRestricted(eq(TENANT), file.capture(), eq("STATEMENT"), eq(ErEvidenceRules.SOURCE), eq(ErEvidenceRules.ENTITY), eq(theCase.getId()), eq(HR_USER), eq("Hanna"));
        assertThat(file.getValue().getContentType()).isEqualTo("application/pdf");   // not the browser's text/html
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("witness.pdf");
        ArgumentCaptor<HrErEvent> event = ArgumentCaptor.forClass(HrErEvent.class);
        verify(h.events).save(event.capture());
        assertThat(event.getValue().getAction()).isEqualTo("EVIDENCE_ADDED");
        assertThat(event.getValue().getNote()).contains("Statement").contains("witness.pdf");
        assertThat(event.getValue().isVisibleToEmployee()).isFalse();
    }

    @Test
    void aFileThatIsNotWhatItsNameSaysIsRefusedAndNothingIsStored() {
        when(facade.listRestricted(any(), anyString(), anyString(), any())).thenReturn(List.of());
        MockMultipartFile page = new MockMultipartFile("file", "statement.pdf", "application/pdf", "<html></html>".getBytes());
        assertThatThrownBy(() -> service.add(TENANT, theCase.getId(), "STATEMENT", page, MANAGE, HR_USER, "Hanna")).isInstanceOf(IllegalArgumentException.class);
        verify(facade, never()).attachRestricted(any(), any(), anyString(), anyString(), anyString(), any(), any(), any());
        verify(h.events, never()).save(any());
    }

    @Test
    void readingNeedsEmployeeRelationsReadAndAddingNeedsManage() {
        assertThatThrownBy(() -> service.list(TENANT, theCase.getId(), NOTHING, HR_USER)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.add(TENANT, theCase.getId(), "STATEMENT", pdf("a.pdf"), DECIDE, HR_USER, "Hanna")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.remove(TENANT, theCase.getId(), UUID.randomUUID(), "Wrong case", DECIDE, HR_USER)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(facade);
    }

    @Test
    void nobodyOpensTheFilesOfACaseAboutThemselves() {
        h.signedInAs(HR_USER, emp);
        assertThatThrownBy(() -> service.list(TENANT, theCase.getId(), MANAGE, HR_USER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.download(TENANT, theCase.getId(), UUID.randomUUID(), MANAGE, HR_USER)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(facade);
    }

    @Test
    void aCaseThatIsClosedTakesNoNewFilesAndKeepsNoneRemoved() {
        theCase.moveTo(ErRules.CLOSED);
        assertThatThrownBy(() -> service.add(TENANT, theCase.getId(), "STATEMENT", pdf("a.pdf"), MANAGE, HR_USER, "Hanna")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.remove(TENANT, theCase.getId(), UUID.randomUUID(), "Wrong case", MANAGE, HR_USER)).isInstanceOf(IllegalArgumentException.class);
        verify(facade, never()).attachRestricted(any(), any(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void removingAFileNeedsAReasonDetachesItAndNotesItOnTheTimeline() {
        UUID fileId = UUID.randomUUID();
        when(facade.listRestricted(any(), anyString(), anyString(), any())).thenReturn(List.of(stored(fileId, "witness.pdf")));
        assertThatThrownBy(() -> service.remove(TENANT, theCase.getId(), fileId, " ", MANAGE, HR_USER)).isInstanceOf(IllegalArgumentException.class);
        verify(facade, never()).detachRestricted(any(), anyString(), anyString(), any(), any());

        service.remove(TENANT, theCase.getId(), fileId, "Uploaded to the wrong case", MANAGE, HR_USER);
        verify(facade).detachRestricted(TENANT, ErEvidenceRules.SOURCE, ErEvidenceRules.ENTITY, theCase.getId(), fileId);
        ArgumentCaptor<HrErEvent> event = ArgumentCaptor.forClass(HrErEvent.class);
        verify(h.events).save(event.capture());
        assertThat(event.getValue().getAction()).isEqualTo("EVIDENCE_REMOVED");
        assertThat(event.getValue().getNote()).contains("witness.pdf").contains("wrong case");
    }

    @Test
    void aFileThatIsNotOnThisCaseCannotBeRemovedThroughIt() {
        when(facade.listRestricted(any(), anyString(), anyString(), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.remove(TENANT, theCase.getId(), UUID.randomUUID(), "Wrong case", MANAGE, HR_USER)).isInstanceOf(ResourceNotFoundException.class);
        verify(facade, never()).detachRestricted(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void downloadsGoThroughTheCaseSoAFileOfAnotherCaseIsNotReachable() {
        UUID fileId = UUID.randomUUID();
        when(facade.downloadRestricted(TENANT, ErEvidenceRules.SOURCE, ErEvidenceRules.ENTITY, theCase.getId(), fileId))
                .thenReturn(new EvidenceFacade.DownloadedEvidence(new byte[]{1}, "a.pdf", "application/pdf"));
        assertThat(service.download(TENANT, theCase.getId(), fileId, MANAGE, HR_USER).fileName()).isEqualTo("a.pdf");

        HrErCase other = h.discipline(h.employee());
        when(facade.downloadRestricted(TENANT, ErEvidenceRules.SOURCE, ErEvidenceRules.ENTITY, other.getId(), fileId)).thenThrow(new ResourceNotFoundException("Evidence", fileId.toString()));
        assertThatThrownBy(() -> service.download(TENANT, other.getId(), fileId, MANAGE, HR_USER)).isInstanceOf(ResourceNotFoundException.class);
    }
}
