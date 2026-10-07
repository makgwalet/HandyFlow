package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Evidence on a screening: the file is stored against the screening, and one record's files are unreachable through another's URL. */
@ExtendWith(MockitoExtension.class)
class GuardScreeningEvidenceServiceTest {

    @Mock private GuardScreeningService screeningService;
    @Mock private EvidenceFacade evidenceFacade;

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private final UUID guardId = UUID.randomUUID();
    private final UUID screeningId = UUID.randomUUID();

    private GuardScreeningEvidenceService service() { return new GuardScreeningEvidenceService(screeningService, evidenceFacade); }

    private static EvidenceResponse ev(UUID id) { return new EvidenceResponse(id, "a.pdf", "application/pdf", 3L, "Certificate", "ACTIVE", "Sam", Instant.now()); }

    @Test @DisplayName("Attaches the file against the screening record with the label as its type")
    void attaches() {
        var file = new MockMultipartFile("file", "clear.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(evidenceFacade.attach(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(ev(UUID.randomUUID()));

        service().attach(TENANT, guardId, screeningId, file, " Clearance certificate ", USER, "Sam");

        var type = ArgumentCaptor.forClass(String.class);
        verify(evidenceFacade).attach(eq(TENANT), eq(file), type.capture(), eq("security"), eq("GuardScreeningRecord"), eq(screeningId), eq(null), eq(USER), eq("Sam"));
        assertThat(type.getValue()).isEqualTo("Clearance certificate");
        verify(screeningService).findForGuard(TENANT, guardId, screeningId);
    }

    @Test @DisplayName("Refuses an empty upload")
    void emptyFile() {
        var empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> service().attach(TENANT, guardId, screeningId, empty, null, USER, "Sam"))
                .isInstanceOf(HandyFlowException.class);
        verify(evidenceFacade, never()).attach(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("Download and remove refuse a file that belongs to a different record")
    void foreignFile() {
        UUID mine = UUID.randomUUID(), other = UUID.randomUUID();
        when(evidenceFacade.listFor(TENANT, "security", "GuardScreeningRecord", screeningId)).thenReturn(List.of(ev(mine)));

        assertThatThrownBy(() -> service().download(TENANT, guardId, screeningId, other)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().remove(TENANT, guardId, screeningId, other)).isInstanceOf(ResourceNotFoundException.class);
        verify(evidenceFacade, never()).download(any(), any());
        verify(evidenceFacade, never()).detach(any(), any());
    }

    @Test @DisplayName("Removes a file that belongs to the record")
    void removesOwn() {
        UUID mine = UUID.randomUUID();
        when(evidenceFacade.listFor(TENANT, "security", "GuardScreeningRecord", screeningId)).thenReturn(List.of(ev(mine)));
        service().remove(TENANT, guardId, screeningId, mine);
        verify(evidenceFacade).detach(TENANT, mine);
    }
}
