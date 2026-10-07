package za.co.handyflow.platform.compliancetender.application.internal.submission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPackageDraft;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPackageDraftRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenderPackageDraftServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID tenderId = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final TenderPackageDraftRepository drafts = mock(TenderPackageDraftRepository.class);
    private final TenderRepository tenders = mock(TenderRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TenderPackageDraftService service = new TenderPackageDraftService(drafts, tenders, mapper);

    private JsonNode json(String s) throws Exception { return mapper.readTree(s); }

    private void tenderExists() {
        when(tenders.findByIdForTenant(tenant, tenderId)).thenReturn(Optional.of(mock(Tender.class)));
    }

    @Test
    @DisplayName("a tender with nothing saved has no draft; an unknown tender is not found")
    void findNone() {
        tenderExists();
        when(drafts.findByTender(tenant, tenderId)).thenReturn(Optional.empty());
        assertThat(service.find(tenant, tenderId)).isEmpty();

        UUID other = UUID.randomUUID();
        when(tenders.findByIdForTenant(tenant, other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find(tenant, other)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the first save creates the draft and the next replaces it, returning what was sent and who saved it")
    void saveCreatesThenReplaces() throws Exception {
        tenderExists();
        when(drafts.findByTender(tenant, tenderId)).thenReturn(Optional.empty());
        when(drafts.save(any(TenderPackageDraft.class))).thenAnswer(i -> i.getArgument(0));

        var first = service.save(tenant, tenderId, json("{\"coverLetter\":\"Dear Sir\"}"), user, "Sam");
        assertThat(first.data().get("coverLetter").asText()).isEqualTo("Dear Sir");
        assertThat(first.updatedByName()).isEqualTo("Sam");

        TenderPackageDraft existing = TenderPackageDraft.create(tenant, tenderId, "{\"coverLetter\":\"old\"}", user, "Sam");
        when(drafts.findByTender(tenant, tenderId)).thenReturn(Optional.of(existing));
        var second = service.save(tenant, tenderId, json("{\"coverLetter\":\"new\"}"), user, "Lerato");
        assertThat(second.data().get("coverLetter").asText()).isEqualTo("new");
        assertThat(second.updatedByName()).isEqualTo("Lerato");
        assertThat(existing.getData()).contains("new");
    }

    @Test
    @DisplayName("only an object under the size limit can be saved")
    void rules() throws Exception {
        tenderExists();
        when(drafts.findByTender(tenant, tenderId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.save(tenant, tenderId, json("[1,2]"), user, "Sam")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(tenant, tenderId, null, user, "Sam")).isInstanceOf(IllegalArgumentException.class);
        String big = "{\"x\":\"" + "a".repeat(TenderPackageDraft.MAX_CHARS) + "\"}";
        assertThatThrownBy(() -> service.save(tenant, tenderId, json(big), user, "Sam")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("too large");
    }

    @Test
    @DisplayName("discard removes the saved draft and does nothing when there is none")
    void discard() {
        tenderExists();
        TenderPackageDraft existing = TenderPackageDraft.create(tenant, tenderId, "{}", user, "Sam");
        when(drafts.findByTender(tenant, tenderId)).thenReturn(Optional.of(existing));
        service.discard(tenant, tenderId);
        verify(drafts).delete(existing);

        when(drafts.findByTender(tenant, tenderId)).thenReturn(Optional.empty());
        service.discard(tenant, tenderId);
        verify(drafts, times(1)).delete(any());
    }
}
