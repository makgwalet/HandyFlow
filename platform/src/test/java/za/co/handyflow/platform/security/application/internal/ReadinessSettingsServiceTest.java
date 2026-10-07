package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.GuardReadinessSettings;
import za.co.handyflow.platform.security.domain.repository.GuardReadinessSettingsRepository;
import za.co.handyflow.platform.security.dto.ReadinessSettingsDtos.SaveReadinessSettingsRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadinessSettingsServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private final GuardReadinessSettingsRepository repo = mock(GuardReadinessSettingsRepository.class);
    private final ReadinessSettingsService service = new ReadinessSettingsService(repo);

    @Test @DisplayName("Defaults apply until something is saved")
    void defaults() {
        when(repo.findForTenant(TENANT)).thenReturn(Optional.empty());
        assertThat(service.effective(TENANT)).isEqualTo(GuardReadinessCalculator.Requirements.defaults());
        var dto = service.get(TENANT);
        assertThat(dto.customised()).isFalse();
        assertThat(dto.requiredScreening()).containsExactly("CRIMINAL_RECORD_CHECK", "REFERENCE_CHECK", "DRUG_TEST");
        assertThat(dto.requiredDocuments()).containsExactly("ID_COPY");
        assertThat(dto.screeningOptions()).extracting("value").doesNotContain("OTHER");
        assertThat(dto.documentOptions()).extracting("value").contains("POPIA_CONSENT", "POLICE_CLEARANCE").doesNotContain("OTHER");
    }

    @Test @DisplayName("A saved choice is cleaned, stored and read back")
    void saves() {
        when(repo.findForTenant(TENANT)).thenReturn(Optional.empty());
        var out = service.save(TENANT, new SaveReadinessSettingsRequest(List.of("drug_test", " CREDIT_CHECK ", ""), List.of("ID_COPY", "POPIA_CONSENT", "ID_COPY")), "Sam");
        assertThat(out.customised()).isTrue();
        assertThat(out.requiredScreening()).containsExactly("DRUG_TEST", "CREDIT_CHECK");
        assertThat(out.requiredDocuments()).containsExactly("ID_COPY", "POPIA_CONSENT");
        verify(repo).save(any(GuardReadinessSettings.class));
    }

    @Test @DisplayName("An unknown or catch-all value is refused")
    void refuses() {
        when(repo.findForTenant(TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.save(TENANT, new SaveReadinessSettingsRequest(List.of("TEA_BREAK"), List.of()), "Sam"))
                .isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.save(TENANT, new SaveReadinessSettingsRequest(List.of(), List.of("OTHER")), "Sam"))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("A tenant may require nothing beyond PSiRA")
    void emptyIsAllowed() {
        when(repo.findForTenant(TENANT)).thenReturn(Optional.empty());
        var out = service.save(TENANT, new SaveReadinessSettingsRequest(List.of(), List.of()), "Sam");
        assertThat(out.requiredScreening()).isEmpty();
        assertThat(out.requiredDocuments()).isEmpty();
    }
}
