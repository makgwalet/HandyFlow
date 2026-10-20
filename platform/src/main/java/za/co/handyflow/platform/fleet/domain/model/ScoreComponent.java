package za.co.handyflow.platform.fleet.domain.model;

/**
 * The parts of a driver's Safety &amp; Performance Score. Each is scored 0–100 from real data,
 * or reported as NO_DATA when there isn't enough — in which case its weight is spread
 * over the components that do have data (see DriverScoreCalculator), so a missing data
 * source never silently drags a score down.
 * <p>
 * SAFETY is built from telematics events (speeding, harsh braking / acceleration / cornering,
 * idling) on trips the driver made in vehicles that have a telematics unit; it is NO_DATA until
 * a driver has driven enough tracked kilometres.
 */
public enum ScoreComponent {
    SAFETY("Safety (telematics)"),
    INSPECTION("Inspection compliance"),
    ACCIDENTS("Accident record"),
    FUEL("Fuel efficiency"),
    TRIPS("Trip compliance"),
    COMPLIANCE("Licence & PrDP compliance");

    private final String label;

    ScoreComponent(String label) { this.label = label; }

    public String label() { return label; }

    public int weight(DriverScoreSettings s) {
        return switch (this) {
            case SAFETY -> s.getWeightSafety();
            case INSPECTION -> s.getWeightInspection();
            case ACCIDENTS -> s.getWeightAccidents();
            case FUEL -> s.getWeightFuel();
            case TRIPS -> s.getWeightTrips();
            case COMPLIANCE -> s.getWeightCompliance();
        };
    }
}