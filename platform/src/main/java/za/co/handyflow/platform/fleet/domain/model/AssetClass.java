package za.co.handyflow.platform.fleet.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * What kind of asset a vehicle record is. A vehicle with no {@link AssetProfile} is a ROAD_VEHICLE.
 * <ul>
 *   <li>HEAVY_VEHICLE — trucks and tractor units: road assets that may also pull trailers.</li>
 *   <li>TRACTOR, PLANT, FORKLIFT, SPECIALISED — worked in hours, not kilometres: engine / PTO /
 *       hydraulic hours, hour-based servicing, utilisation, implements.</li>
 *   <li>TRAILER — a registered, unpowered asset that is coupled to a hauling vehicle.</li>
 * </ul>
 */
public enum AssetClass {
    ROAD_VEHICLE, HEAVY_VEHICLE, TRACTOR, PLANT, TRAILER, FORKLIFT, SPECIALISED;

    /** Classes whose working life is measured in engine hours. */
    public static final Set<AssetClass> HOURS_BASED = EnumSet.of(TRACTOR, PLANT, FORKLIFT, SPECIALISED);

    public boolean isHoursBased() { return HOURS_BASED.contains(this); }
}