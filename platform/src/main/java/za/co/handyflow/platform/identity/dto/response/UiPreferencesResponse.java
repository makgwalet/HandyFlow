package za.co.handyflow.platform.identity.dto.response;

import java.util.List;

/**
 * Everything the frontend needs to render the shell:
 * <ul>
 *   <li>{@code effective} - what to apply now (already resolved);</li>
 *   <li>{@code tenant} - tenant defaults, for the admin settings screen;</li>
 *   <li>{@code user} - the user's raw overrides (null = inherit), for the
 *       customizer; null during a read-only support session;</li>
 *   <li>{@code canEditTenant} - whether to show tenant-level controls.</li>
 * </ul>
 */
public record UiPreferencesResponse(
        Effective effective,
        TenantDefaults tenant,
        UserOverrides user,
        boolean canEditTenant
) {
    public record Effective(String theme, String sidebar, String container, String brandColor,
                            boolean brandLocked, List<String> pinnedModules) {}

    public record TenantDefaults(String brandColor, boolean brandLocked, String defaultTheme,
                                 String defaultSidebar, String defaultContainer) {}

    public record UserOverrides(String theme, String sidebar, String container, String brandColor,
                                List<String> pinnedModules) {}
}
