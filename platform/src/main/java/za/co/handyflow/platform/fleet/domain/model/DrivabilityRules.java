package za.co.handyflow.platform.fleet.domain.model;

import java.util.List;
import java.util.Set;

/**
 * A vehicle that cannot be driven after an accident must not be sent out. The block lasts while any accident case that says "cannot be driven" is still
 * open (reported, under assessment, in repair) or ended in a write-off; closing the case, or correcting the case to say the vehicle can be driven, lifts it.
 */
public final class DrivabilityRules {
    private DrivabilityRules() {}

    /** Case states in which an "undrivable" finding still stands. */
    public static final Set<AccidentStatus> BLOCKING = Set.of(AccidentStatus.REPORTED, AccidentStatus.UNDER_ASSESSMENT, AccidentStatus.IN_REPAIR, AccidentStatus.WRITTEN_OFF);

    public static boolean blocks(Accident a) {
        return !a.isVehicleDrivable() && BLOCKING.contains(a.getStatus());
    }

    /** Null when the vehicle may go out; otherwise why not (names the case so the dispatcher knows where to look). */
    public static String problem(List<Accident> accidentsOnVehicle) {
        List<String> refs = accidentsOnVehicle.stream().filter(DrivabilityRules::blocks).map(Accident::getReferenceNumber).toList();
        if (refs.isEmpty()) return null;
        return "This vehicle cannot be driven after an accident (" + String.join(", ", refs)
                + "). Close the accident case, or correct it if the vehicle can be driven, before sending it out.";
    }
}
