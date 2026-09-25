package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.model.*;
import za.co.handyflow.platform.identity.domain.repository.TenantUiPreferencesRepository;
import za.co.handyflow.platform.identity.domain.repository.UserUiPreferencesRepository;
import za.co.handyflow.platform.identity.dto.request.UpdateTenantUiPreferencesRequest;
import za.co.handyflow.platform.identity.dto.request.UpdateUserUiPreferencesRequest;
import za.co.handyflow.platform.identity.dto.response.UiPreferencesResponse;
import za.co.handyflow.platform.shared.BusinessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads and writes appearance preferences. Tenant and user ids always come
 * from the authenticated context (never from the request body), and user rows
 * are always looked up by (userId, tenantId).
 */
@Service
@RequiredArgsConstructor
public class UiPreferencesService {

    private final TenantUiPreferencesRepository tenantRepository;
    private final UserUiPreferencesRepository userRepository;

    /**
     * @param userId null during a read-only support session; the response then
     *               carries tenant defaults only.
     */
    @Transactional(readOnly = true)
    public UiPreferencesResponse get(UUID tenantId, UUID userId, boolean canEditTenant) {
        Objects.requireNonNull(tenantId, "tenantId");
        TenantUiPreferences tenant = loadTenant(tenantId);
        UserUiPreferences user = userId == null ? null
                : userRepository.findByUserIdAndTenantId(userId, tenantId).orElse(null);
        return UiPreferencesResolver.resolve(tenant, user, canEditTenant);
    }

    @Transactional
    public UiPreferencesResponse updateTenant(UUID tenantId, UUID actingUserId,
                                              UpdateTenantUiPreferencesRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        UiBrandColor brand = required(UiBrandColor.parseNullable(req.brandColor()), "brandColor");
        UiThemeMode theme = required(UiThemeMode.parseNullable(req.defaultTheme()), "defaultTheme");
        UiSidebarMode sidebar = required(UiSidebarMode.parseNullable(req.defaultSidebar()), "defaultSidebar");
        UiContainerMode container = required(UiContainerMode.parseNullable(req.defaultContainer()), "defaultContainer");
        boolean locked = Boolean.TRUE.equals(req.brandLocked());

        TenantUiPreferences tenant = loadTenant(tenantId);
        tenant.update(brand, locked, theme, sidebar, container, actingUserId);
        TenantUiPreferences saved = tenantRepository.save(tenant);

        UserUiPreferences user = actingUserId == null ? null
                : userRepository.findByUserIdAndTenantId(actingUserId, tenantId).orElse(null);
        return UiPreferencesResolver.resolve(saved, user, true);
    }

    @Transactional
    public UiPreferencesResponse updateUser(UUID tenantId, UUID userId, boolean canEditTenant,
                                            UpdateUserUiPreferencesRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (userId == null) {
            throw new BusinessException("Personal preferences can't be changed during a read-only support session.");
        }
        UiThemeMode theme = UiThemeMode.parseNullable(req.theme());
        UiSidebarMode sidebar = UiSidebarMode.parseNullable(req.sidebar());
        UiContainerMode container = UiContainerMode.parseNullable(req.container());
        UiBrandColor brand = UiBrandColor.parseNullable(req.brandColor());
        List<String> pinned = dedupe(req.pinnedModules());

        TenantUiPreferences tenant = loadTenant(tenantId);
        if (brand != null && tenant.isBrandLocked()) {
            throw new BusinessException("Your organisation has locked the brand colour.");
        }

        UserUiPreferences user = userRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseGet(() -> {
                    // A row for this user id under a different tenant means the
                    // token and the stored data disagree; refuse rather than
                    // silently creating a duplicate key collision.
                    if (userRepository.existsById(userId)) {
                        throw new BusinessException("Preferences belong to a different organisation.");
                    }
                    return UserUiPreferences.create(userId, tenantId);
                });
        user.update(theme, sidebar, container, brand, pinned);
        UserUiPreferences saved = userRepository.save(user);
        return UiPreferencesResolver.resolve(tenant, saved, canEditTenant);
    }

    private TenantUiPreferences loadTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElseGet(() -> TenantUiPreferences.systemDefaults(tenantId));
    }

    private static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static List<String> dedupe(List<String> keys) {
        if (keys == null) return List.of();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String k : keys) if (k != null && !k.isBlank()) unique.add(k);
        return List.copyOf(unique);
    }
}
