package za.co.handyflow.platform.compliancetender.application.internal.submission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.compliancetender.domain.model.TenderSubmissionProfile;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderSubmissionProfileRepository;
import za.co.handyflow.platform.compliancetender.dto.SubmissionProfileRequest;
import za.co.handyflow.platform.compliancetender.dto.SubmissionProfileResponse;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionProfileServiceTest {

    private final TenderSubmissionProfileRepository repo = mock(TenderSubmissionProfileRepository.class);
    private final SubmissionProfileService service = new SubmissionProfileService(repo);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID user = UUID.randomUUID();

    private static SubmissionProfileRequest request(String name, Set<String> ext) {
        return new SubmissionProfileRequest(name, ext, 500L * 1024 * 1024, 4096L * 1024 * 1024, null, false, 50);
    }

    @Test
    @DisplayName("create saves the profile; extensions are cleaned to lower case without dots")
    void creates() {
        when(repo.nameTaken(any(), any(), any())).thenReturn(false);
        when(repo.save(any(TenderSubmissionProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmissionProfileResponse r = service.create(tenant, request("Eskom", Set.of(".PDF", " xlsx ", "")), user);

        assertThat(r.name()).isEqualTo("Eskom");
        assertThat(r.allowedExtensions()).isEqualTo(Set.of("pdf", "xlsx"));
        assertThat(r.zipAllowed()).isEqualTo(false);
        assertThat(r.maxFileNameLength()).isEqualTo(50);
    }

    @Test
    @DisplayName("a name already used by this tenant is refused")
    void duplicateName() {
        when(repo.nameTaken(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(tenant, request("Eskom", null), user)).isInstanceOf(BusinessException.class);
        verify(repo, never()).save(any(TenderSubmissionProfile.class));
    }

    @Test
    @DisplayName("loading with no id is null; an id that is not this tenant's is a not-found, never ignored")
    void load() {
        assertThat(service.load(tenant, null)).isNull();
        UUID id = UUID.randomUUID();
        when(repo.findByIdForTenant(tenant, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.load(tenant, id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a saved profile becomes the domain profile with its limits")
    void toDomain() {
        UUID id = UUID.randomUUID();
        TenderSubmissionProfile saved = TenderSubmissionProfile.create(tenant, "Eskom", Set.of("pdf"), 10L, 20L, 3, true, 40, user);
        when(repo.findByIdForTenant(tenant, id)).thenReturn(Optional.of(saved));

        SubmissionProfile p = service.load(tenant, id);

        assertThat(p.name()).isEqualTo("Eskom");
        assertThat(p.allowedExtensions()).isEqualTo(Set.of("pdf"));
        assertThat(p.maxFileBytes()).isEqualTo(10L);
        assertThat(p.maxTotalBytes()).isEqualTo(20L);
        assertThat(p.maxFileCount()).isEqualTo(3);
        assertThat(p.offersZip()).isEqualTo(true);
    }

    @Test
    @DisplayName("an empty extension list means not restricted, not 'nothing allowed'")
    void emptyExtensions() {
        TenderSubmissionProfile saved = TenderSubmissionProfile.create(tenant, "Open", Set.of(), null, null, null, null, null, user);
        assertThat(saved.extensionSet()).isNull();
    }

    @Test
    @DisplayName("a limit of zero is refused by the entity")
    void zeroLimit() {
        assertThatThrownBy(() -> TenderSubmissionProfile.create(tenant, "x", null, 0L, null, null, null, null, user)).isInstanceOf(IllegalArgumentException.class);
    }
}
