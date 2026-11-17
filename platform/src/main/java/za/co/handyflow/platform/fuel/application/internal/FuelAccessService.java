package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.dto.FuelAccessDtos.AccessSummary;
import za.co.handyflow.platform.fuel.dto.FuelProfileDtos.ProfileView;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Puts together what the mobile app needs to choose its menu. Read-only; every real check is still made by the server on each call. */
@Service
@RequiredArgsConstructor
public class FuelAccessService {

    private final FuelOperatingProfileService profile;
    private final FuelScopeService scopes;
    private final FuelSettingsService settings;

    @Transactional(readOnly = true)
    public AccessSummary summary(TenantId tenantId, UUID userId) {
        ProfileView p = profile.view(tenantId);
        return new AccessSummary(p.legacyMode(),
                p.activeDivisions().stream().map(Enum::name).sorted().toList(),
                scopes.mine(tenantId, userId),
                settings.emergencyContact(tenantId));
    }
}
