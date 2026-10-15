package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Pure rules for Earthmoving (patch E1). Every method throws IllegalArgumentException with a message a person can act
 * on, or IllegalStateException where the record is in the wrong state. No database, no Spring: unit-testable directly.
 * The allowed value lists match the database check constraints (V13, V128, V424); keep them in step.
 */
public final class EarthmovingRules {
    private EarthmovingRules() {}

    public static final Set<String> ASSET_TYPES = Set.of("DOZER", "EXCAVATOR", "GRADER", "LOADER", "DUMPER", "CRANE",
            "ROLLER", "SCRAPER", "COMPACTOR", "DRILL", "OTHER");
    public static final Set<String> OWNERSHIP = Set.of("OWN", "HIRED_IN", "HIRED_OUT");
    public static final Set<String> MAINTENANCE_TYPES = Set.of("SERVICE", "REPAIR", "INSPECTION", "TYRE", "BATTERY",
            "ELECTRICAL", "HYDRAULICS", "ENGINE", "TRACKS", "OTHER");
    public static final Set<String> INCIDENT_TYPES = Set.of("BREAKDOWN", "ACCIDENT", "THEFT", "FIRE", "ROLLOVER",
            "NEAR_MISS", "FUEL_SPILL", "OTHER");
    public static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public static final String READ = "EARTHMOVING_READ";
    public static final String WRITE = "EARTHMOVING_WRITE";
    public static final String DEPLOY = "EARTHMOVING_DEPLOY";
    public static final String MAINTAIN = "EARTHMOVING_MAINTAIN";
    public static final String RELEASE = "EARTHMOVING_RELEASE";
    public static final String ADMIN = "EARTHMOVING_ADMIN";

    private static final int MIN_REASON = 5;

    public static String oneOf(String value, Set<String> allowed, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        String v = value.trim().toUpperCase();
        if (!allowed.contains(v)) {
            throw new IllegalArgumentException("Unknown " + label.toLowerCase() + " \"" + value + "\". Allowed: "
                    + String.join(", ", new java.util.TreeSet<>(allowed)) + ".");
        }
        return v;
    }

    /** Checks an asset's descriptive fields for create and edit. Returns the cleaned ownership type. */
    public static String checkAssetDetails(String name, String assetType, String ownershipType, Integer year,
                                           BigDecimal hourlyRate, BigDecimal dailyRate, LocalDate hireStart,
                                           LocalDate hireEnd, BigDecimal serviceIntervalHours, int thisYear) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Asset name is required.");
        oneOf(assetType, ASSET_TYPES, "Asset type");
        String own = ownershipType == null || ownershipType.isBlank() ? "OWN" : oneOf(ownershipType, OWNERSHIP, "Ownership");
        if (year != null && (year < 1950 || year > thisYear + 1))
            throw new IllegalArgumentException("Year must be between 1950 and " + (thisYear + 1) + ".");
        if (hourlyRate != null && hourlyRate.signum() < 0) throw new IllegalArgumentException("Hourly rate cannot be negative.");
        if (dailyRate != null && dailyRate.signum() < 0) throw new IllegalArgumentException("Daily rate cannot be negative.");
        if (hireStart != null && hireEnd != null && hireEnd.isBefore(hireStart))
            throw new IllegalArgumentException("Hire end date cannot be before the hire start date.");
        if (serviceIntervalHours != null && serviceIntervalHours.signum() <= 0)
            throw new IllegalArgumentException("Service interval must be more than zero hours.");
        return own;
    }

    /** A reason of at least 5 characters, trimmed. */
    public static String requireReason(String reason, String what) {
        if (reason == null || reason.trim().length() < MIN_REASON)
            throw new IllegalArgumentException("Give a reason of at least " + MIN_REASON + " characters for " + what + ".");
        return reason.trim();
    }

    /**
     * Authority needed to move a machine from one status to another through the status endpoint, or null when the
     * target is not allowed there at all. DEPLOYED is never set through the status endpoint: use deploy, which records
     * the site and client (finding B3). Release from maintenance or breakdown is its own authority.
     */
    public static String authorityForStatusChange(AssetStatus from, AssetStatus to) {
        return switch (to) {
            case DEPLOYED -> null;
            case BREAKDOWN -> WRITE;                       // anyone who works with the fleet can report a breakdown
            case MAINTENANCE -> MAINTAIN;
            case HIRED_OUT -> DEPLOY;
            case RETIRED -> ADMIN;
            case AVAILABLE -> (from == AssetStatus.MAINTENANCE || from == AssetStatus.BREAKDOWN) ? RELEASE : DEPLOY;
        };
    }

    /** True when the change is a release back into service, which needs a reason and no open critical incident. */
    public static boolean isRelease(AssetStatus from, AssetStatus to) {
        return to == AssetStatus.AVAILABLE && (from == AssetStatus.MAINTENANCE || from == AssetStatus.BREAKDOWN);
    }

    /** Whether the change needs a written reason. Returning a deployed machine to the yard does not. */
    public static boolean needsReason(AssetStatus from, AssetStatus to) {
        return !(from == AssetStatus.DEPLOYED && to == AssetStatus.AVAILABLE);
    }

    public static void checkDeployable(AssetStatus status, long openCriticalIncidents, String ownership,
                                       LocalDate hireEnd, LocalDate today) {
        if (openCriticalIncidents > 0)
            throw new IllegalStateException("This machine has " + openCriticalIncidents
                    + " unresolved critical incident(s). Resolve them before it can be deployed.");
        if ("HIRED_IN".equals(ownership) && hireEnd != null && hireEnd.isBefore(today))
            throw new IllegalStateException("The hire period for this machine ended on " + hireEnd
                    + ". Extend the hire before deploying it.");
        if (status != AssetStatus.AVAILABLE)
            throw new IllegalStateException("Only an available machine can be deployed. This one is " + status.name() + ".");
    }

    public static void checkDeployDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start))
            throw new IllegalArgumentException("Expected end date cannot be before the start date.");
    }

    /** Shift start. A start reading below the machine's current reading is an unexplained decrease. */
    public static void checkShiftStart(AssetStatus status, BigDecimal startHours, BigDecimal assetHours) {
        if (status == AssetStatus.MAINTENANCE || status == AssetStatus.BREAKDOWN
                || status == AssetStatus.RETIRED || status == AssetStatus.HIRED_OUT)
            throw new IllegalStateException("A shift cannot start on a machine that is " + status.name()
                    + ". Release it first.");
        if (startHours != null) {
            if (startHours.signum() < 0) throw new IllegalArgumentException("Start hours cannot be negative.");
            if (assetHours != null && startHours.compareTo(assetHours) < 0)
                throw new IllegalArgumentException("Start reading (" + startHours + ") is below the machine's current reading ("
                        + assetHours + "). Hour meters only count up; if the meter was replaced or corrected, raise that first.");
        }
    }

    public static void checkShiftEnd(Instant startedAt, Instant endedAt, BigDecimal startHours, BigDecimal endHours,
                                     BigDecimal fuelLitres) {
        if (endedAt == null) throw new IllegalArgumentException("End time is required.");
        if (startedAt != null && endedAt.isBefore(startedAt))
            throw new IllegalArgumentException("The shift cannot end before it started.");
        if (endHours != null && endHours.signum() < 0) throw new IllegalArgumentException("End hours cannot be negative.");
        if (startHours != null && endHours != null && endHours.compareTo(startHours) < 0)
            throw new IllegalArgumentException("End reading (" + endHours + ") is below the start reading (" + startHours + ").");
        if (fuelLitres != null && fuelLitres.signum() < 0) throw new IllegalArgumentException("Fuel used cannot be negative.");
    }

    public static void checkMaintenance(AssetStatus status, String type, BigDecimal hoursAtService, BigDecimal lastServiceHours,
                                        BigDecimal cost) {
        oneOf(type, MAINTENANCE_TYPES, "Maintenance type");
        if (status == AssetStatus.RETIRED) throw new IllegalStateException("A retired machine cannot take new maintenance.");
        if (hoursAtService != null && hoursAtService.signum() < 0) throw new IllegalArgumentException("Hours at service cannot be negative.");
        if (cost != null && cost.signum() < 0) throw new IllegalArgumentException("Cost cannot be negative.");
        if ("SERVICE".equals(type.trim().toUpperCase()) && hoursAtService != null && lastServiceHours != null
                && hoursAtService.compareTo(lastServiceHours) < 0)
            throw new IllegalArgumentException("Service hours (" + hoursAtService + ") are below the last service (" + lastServiceHours + ").");
    }
}
