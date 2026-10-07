package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.security.domain.model.GuardRiskSettings;
import za.co.handyflow.platform.security.domain.repository.GuardRiskSettingsRepository;
import za.co.handyflow.platform.security.dto.GuardPerformanceDtos.SaveRiskSettingsRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Risk thresholds: defaults until saved, validation, and saving updates the one row per tenant. */
@ExtendWith(MockitoExtension.class)
class GuardRiskSettingsServiceTest {

    @Mock private GuardRiskSettingsRepository repository;
    private static final TenantId TENANT = TenantId.generate();
    private GuardRiskSettingsService service() { return new GuardRiskSettingsService(repository); }

    @Test @DisplayName("Uses the defaults and says so until something is saved")
    void defaults() {
        when(repository.findForTenant(TENANT)).thenReturn(Optional.empty());
        var s = service().get(TENANT);
        assertThat(s.customised()).isFalse();
        assertThat(s.reviewAt()).isEqualTo(1);
        assertThat(s.warningAt()).isEqualTo(3);
        assertThat(s.investigationAt()).isEqualTo(5);
        assertThat(s.windowDays()).isEqualTo(90);
        assertThat(service().effective(TENANT)).isEqualTo(GuardRiskEngine.Settings.defaults());
    }

    @Test @DisplayName("Saves new thresholds and reports them as customised")
    void saves() {
        when(repository.findForTenant(TENANT)).thenReturn(Optional.empty());
        var out = service().save(TENANT, new SaveRiskSettingsRequest(2, 4, 6, 60, 3, 180, false), "Sam");
        assertThat(out.customised()).isTrue();
        assertThat(out.warningAt()).isEqualTo(4);
        assertThat(out.suspensionReviewOnCritical()).isFalse();
        assertThat(out.updatedByName()).isEqualTo("Sam");
        verify(repository).save(any(GuardRiskSettings.class));
    }

    @Test @DisplayName("Rejects thresholds that do not rise")
    void rejects() {
        assertThatThrownBy(() -> service().save(TENANT, new SaveRiskSettingsRequest(4, 3, 5, 90, 2, 365, true), "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("rise");
        verify(repository, never()).save(any());
    }
}
