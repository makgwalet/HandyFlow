package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.AllocationSettings;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationStrategy;

public record AllocationSettingsResponse(boolean enabled, AllocationStrategy defaultStrategy, boolean reallocateUnavailable) {
    public static AllocationSettingsResponse of(AllocationSettings s) {
        return new AllocationSettingsResponse(s.isEnabled(), s.getDefaultStrategy(), s.isReallocateUnavailable());
    }
}
