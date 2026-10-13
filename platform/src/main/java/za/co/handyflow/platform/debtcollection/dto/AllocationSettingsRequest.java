package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.AllocationStrategy;

/** enabled turns the 15-minute automatic allocation on; preview and manual allocation work either way. Review a preview before enabling. */
public record AllocationSettingsRequest(boolean enabled, AllocationStrategy defaultStrategy, boolean reallocateUnavailable) {}
