package za.co.handyflow.platform.identity.application.internal;

import za.co.handyflow.platform.identity.domain.model.TenantUiPreferences;
import za.co.handyflow.platform.identity.domain.model.UserUiPreferences;
import za.co.handyflow.platform.identity.dto.response.UiPreferencesResponse;

import java.util.List;
import java.util.Objects;

/**
 * Pure precedence rules for appearance preferences. No I/O, so every rule is
 * unit-testable in isolation.
 * <p>
 * effective = user override ?? tenant default, except brand colour: when the
 * tenant has locked it, the tenant value always wins.
 */
final class UiPreferencesResolver {

    private UiPreferencesResolver() {}

    static UiPreferencesResponse resolve(TenantUiPreferences tenant, UserUiPreferences user, boolean canEditTenant) {
        Objects.requireNonNull(tenant, "tenant preferences (use systemDefaults when no row exists)");

        String theme = first(user == null ? null : name(user.getTheme()), name(tenant.getDefaultTheme()));
        String sidebar = first(user == null ? null : name(user.getSidebar()), name(tenant.getDefaultSidebar()));
        String container = first(user == null ? null : name(user.getContainer()), name(tenant.getDefaultContainer()));
        String brand = tenant.isBrandLocked()
                ? name(tenant.getBrandColor())
                : first(user == null ? null : name(user.getBrandColor()), name(tenant.getBrandColor()));
        List<String> pinned = user == null ? List.of() : user.getPinnedModules();

        var effective = new UiPreferencesResponse.Effective(theme, sidebar, container, brand,
                tenant.isBrandLocked(), pinned);
        var tenantDefaults = new UiPreferencesResponse.TenantDefaults(name(tenant.getBrandColor()),
                tenant.isBrandLocked(), name(tenant.getDefaultTheme()), name(tenant.getDefaultSidebar()),
                name(tenant.getDefaultContainer()));
        var overrides = user == null ? null : new UiPreferencesResponse.UserOverrides(
                name(user.getTheme()), name(user.getSidebar()), name(user.getContainer()),
                name(user.getBrandColor()), user.getPinnedModules());

        return new UiPreferencesResponse(effective, tenantDefaults, overrides, canEditTenant);
    }

    private static String first(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }
}
