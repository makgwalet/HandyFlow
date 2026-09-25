package za.co.handyflow.platform.identity.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.identity.domain.model.*;
import za.co.handyflow.platform.identity.domain.repository.TenantUiPreferencesRepository;
import za.co.handyflow.platform.identity.domain.repository.UserUiPreferencesRepository;
import za.co.handyflow.platform.identity.dto.request.UpdateTenantUiPreferencesRequest;
import za.co.handyflow.platform.identity.dto.request.UpdateUserUiPreferencesRequest;
import za.co.handyflow.platform.identity.dto.response.UiPreferencesResponse;
import za.co.handyflow.platform.shared.BusinessException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for UiPreferencesService and UiPreferencesResolver.
 * Covers precedence, the brand lock, validation, read-only support sessions
 * and tenant isolation of user rows.
 */
@ExtendWith(MockitoExtension.class)
class UiPreferencesServiceTest {

    @Mock TenantUiPreferencesRepository tenantRepository;
    @Mock UserUiPreferencesRepository userRepository;

    private UiPreferencesService service;

    private static final UUID TENANT = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    private static final UUID OTHER_TENANT = UUID.fromString("1f0d7c1e-2b1a-4c55-9a8e-0e3a2b9d7f11");
    private static final UUID USER = UUID.fromString("5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    @BeforeEach
    void setUp() {
        service = new UiPreferencesService(tenantRepository, userRepository);
    }

    private static TenantUiPreferences tenantPrefs(UiBrandColor brand, boolean locked, UiThemeMode theme) {
        TenantUiPreferences t = TenantUiPreferences.systemDefaults(TENANT);
        t.update(brand, locked, theme, UiSidebarMode.FULL, UiContainerMode.BOXED, null);
        return t;
    }

    private static UserUiPreferences userPrefs(UiThemeMode theme, UiBrandColor brand, String... pinned) {
        UserUiPreferences u = UserUiPreferences.create(USER, TENANT);
        u.update(theme, null, null, brand, Arrays.asList(pinned));
        return u;
    }

    private static UpdateUserUiPreferencesRequest userReq(String theme, String brand, List<String> pinned) {
        return new UpdateUserUiPreferencesRequest(theme, null, null, brand, pinned);
    }

    @Nested
    @DisplayName("get / precedence")
    class Precedence {

        @Test
        @DisplayName("no rows at all -> system defaults that match today's look")
        void systemDefaults() {
            when(tenantRepository.findById(TENANT)).thenReturn(Optional.empty());
            when(userRepository.findByUserIdAndTenantId(USER, TENANT)).thenReturn(Optional.empty());

            UiPreferencesResponse r = service.get(TENANT, USER, false);

            assertThat(r.effective().theme()).isEqualTo("LIGHT");
            assertThat(r.effective().sidebar()).isEqualTo("FULL");
            assertThat(r.effective().container()).isEqualTo("BOXED");
            assertThat(r.effective().brandColor()).isEqualTo("NAVY");
            assertThat(r.effective().brandLocked()).isFalse();
            assertThat(r.effective().pinnedModules()).isEmpty();
            assertThat(r.user()).isNull();
        }

        @Test
        @DisplayName("user override beats tenant default; null user field inherits")
        void userOverridesTenant() {
            when(tenantRepository.findById(TENANT))
                    .thenReturn(Optional.of(tenantPrefs(UiBrandColor.TEAL, false, UiThemeMode.LIGHT)));
            when(userRepository.findByUserIdAndTenantId(USER, TENANT))
                    .thenReturn(Optional.of(userPrefs(UiThemeMode.DARK, null, "security", "hr")));

            UiPreferencesResponse r = service.get(TENANT, USER, false);

            assertThat(r.effective().theme()).isEqualTo("DARK");        // user wins
            assertThat(r.effective().brandColor()).isEqualTo("TEAL");   // inherited
            assertThat(r.effective().sidebar()).isEqualTo("FULL");      // inherited
            assertThat(r.effective().pinnedModules()).containsExactly("security", "hr");
            assertThat(r.user().theme()).isEqualTo("DARK");
            assertThat(r.user().brandColor()).isNull();
        }

        @Test
        @DisplayName("locked brand colour ignores a stored user brand override")
        void lockedBrandWins() {
            when(tenantRepository.findById(TENANT))
                    .thenReturn(Optional.of(tenantPrefs(UiBrandColor.CHARCOAL, true, UiThemeMode.LIGHT)));
            when(userRepository.findByUserIdAndTenantId(USER, TENANT))
                    .thenReturn(Optional.of(userPrefs(null, UiBrandColor.VIOLET)));

            UiPreferencesResponse r = service.get(TENANT, USER, false);

            assertThat(r.effective().brandColor()).isEqualTo("CHARCOAL");
            assertThat(r.effective().brandLocked()).isTrue();
        }

        @Test
        @DisplayName("read-only support session (no user) gets tenant defaults and never reads user rows")
        void supportSession() {
            when(tenantRepository.findById(TENANT))
                    .thenReturn(Optional.of(tenantPrefs(UiBrandColor.OCEAN, false, UiThemeMode.SYSTEM)));

            UiPreferencesResponse r = service.get(TENANT, null, false);

            assertThat(r.effective().theme()).isEqualTo("SYSTEM");
            assertThat(r.effective().brandColor()).isEqualTo("OCEAN");
            assertThat(r.user()).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("user rows are only ever looked up with the caller's tenant")
        void tenantScopedLookup() {
            when(tenantRepository.findById(TENANT)).thenReturn(Optional.empty());
            when(userRepository.findByUserIdAndTenantId(USER, TENANT)).thenReturn(Optional.empty());

            service.get(TENANT, USER, false);

            verify(userRepository).findByUserIdAndTenantId(USER, TENANT);
            verify(userRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("creates a row on first save, de-duplicating pinned modules and keeping order")
        void createsRow() {
            when(tenantRepository.findById(TENANT)).thenReturn(Optional.empty());
            when(userRepository.findByUserIdAndTenantId(USER, TENANT)).thenReturn(Optional.empty());
            when(userRepository.existsById(USER)).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UiPreferencesResponse r = service.updateUser(TENANT, USER, false,
                    userReq("dark", null, Arrays.asList("security", "hr", "security", null, " ")));

            ArgumentCaptor<UserUiPreferences> saved = ArgumentCaptor.forClass(UserUiPreferences.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
            assertThat(saved.getValue().getTheme()).isEqualTo(UiThemeMode.DARK);
            assertThat(saved.getValue().getPinnedModules()).containsExactly("security", "hr");
            assertThat(r.effective().theme()).isEqualTo("DARK");
        }

        @Test
        @DisplayName("null fields clear an override back to 'inherit'")
        void nullClearsOverride() {
            UserUiPreferences existing = userPrefs(UiThemeMode.DARK, UiBrandColor.TEAL, "hr");
            when(tenantRepository.findById(TENANT)).thenReturn(Optional.empty());
            when(userRepository.findByUserIdAndTenantId(USER, TENANT)).thenReturn(Optional.of(existing));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UiPreferencesResponse r = service.updateUser(TENANT, USER, false, userReq(null, null, null));

            assertThat(existing.getTheme()).isNull();
            assertThat(existing.getBrandColor()).isNull();
            assertThat(existing.getPinnedModules()).isEmpty();
            assertThat(r.effective().theme()).isEqualTo("LIGHT");
            assertThat(r.effective().brandColor()).isEqualTo("NAVY");
        }

        @Test
        @DisplayName("rejects a brand override when the tenant has locked the brand")
        void rejectsBrandWhenLocked() {
            when(tenantRepository.findById(TENANT))
                    .thenReturn(Optional.of(tenantPrefs(UiBrandColor.NAVY, true, UiThemeMode.LIGHT)));

            assertThatThrownBy(() -> service.updateUser(TENANT, USER, false, userReq(null, "VIOLET", null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("locked");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects unknown enum values with the allowed list")
        void rejectsInvalidValue() {
            assertThatThrownBy(() -> service.updateUser(TENANT, USER, false, userReq("purple", null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("LIGHT");
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("refuses writes during a read-only support session")
        void refusesSupportSession() {
            assertThatThrownBy(() -> service.updateUser(TENANT, null, false, userReq("DARK", null, null)))
                    .isInstanceOf(BusinessException.class);
            verifyNoInteractions(userRepository, tenantRepository);
        }

        @Test
        @DisplayName("refuses to write when the user's row belongs to another tenant")
        void refusesCrossTenantRow() {
            when(tenantRepository.findById(OTHER_TENANT)).thenReturn(Optional.empty());
            when(userRepository.findByUserIdAndTenantId(USER, OTHER_TENANT)).thenReturn(Optional.empty());
            when(userRepository.existsById(USER)).thenReturn(true);

            assertThatThrownBy(() -> service.updateUser(OTHER_TENANT, USER, false, userReq("DARK", null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("different organisation");
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateTenant")
    class UpdateTenant {

        @Test
        @DisplayName("creates the tenant row from defaults and records who changed it")
        void createsTenantRow() {
            when(tenantRepository.findById(TENANT)).thenReturn(Optional.empty());
            when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findByUserIdAndTenantId(USER, TENANT)).thenReturn(Optional.empty());

            UiPreferencesResponse r = service.updateTenant(TENANT, USER,
                    new UpdateTenantUiPreferencesRequest("teal", true, "system", "mini", "full"));

            ArgumentCaptor<TenantUiPreferences> saved = ArgumentCaptor.forClass(TenantUiPreferences.class);
            verify(tenantRepository).save(saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
            assertThat(saved.getValue().getBrandColor()).isEqualTo(UiBrandColor.TEAL);
            assertThat(saved.getValue().isBrandLocked()).isTrue();
            assertThat(saved.getValue().getUpdatedBy()).isEqualTo(USER);
            assertThat(r.effective().sidebar()).isEqualTo("MINI");
            assertThat(r.canEditTenant()).isTrue();
        }

        @Test
        @DisplayName("rejects an unknown brand colour without saving")
        void rejectsUnknownBrand() {
            assertThatThrownBy(() -> service.updateTenant(TENANT, USER,
                    new UpdateTenantUiPreferencesRequest("#ff00ff", false, "LIGHT", "FULL", "BOXED")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("UiBrandColor");
            verify(tenantRepository, never()).save(any());
        }
    }
}
