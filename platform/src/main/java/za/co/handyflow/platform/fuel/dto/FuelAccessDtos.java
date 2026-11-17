package za.co.handyflow.platform.fuel.dto;

import java.util.List;

/** B9: what the signed-in person can reach in Fuel, for the mobile app's menu. Permissions themselves are already in the sign-in. */
public final class FuelAccessDtos {
    private FuelAccessDtos() {}

    /**
     * @param legacyMode  the company has not set up divisions, so every division is open
     * @param activeDivisions the divisions switched on for the company (FuelDivision names)
     * @param scope       this person's own limits, if any
     * @param emergencyContact the control-room contact, or an empty one
     */
    public record AccessSummary(boolean legacyMode, List<String> activeDivisions, ScopeDtos.MyScope scope,
                                FuelSettingsDtos.EmergencyContact emergencyContact) {}
}
