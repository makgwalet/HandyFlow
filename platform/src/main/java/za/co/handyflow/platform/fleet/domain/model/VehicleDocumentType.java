package za.co.handyflow.platform.fleet.domain.model;

/**
 * Kinds of document in a vehicle's vault.
 * <ul>
 *   <li><b>singleton</b> — a vehicle has one current document of this type; uploading a new
 *       one supersedes the previous (kept as history, version incremented). Licence disc,
 *       roadworthy, insurance, etc.</li>
 *   <li><b>tracksVehicleExpiry</b> — the document's expiry date is mandatory and is written
 *       through to the matching expiry field on {@link Vehicle}, which is what the existing
 *       FleetNotificationScheduler already alerts on. One source of truth, no scheduler change.</li>
 * </ul>
 * Multi-instance types (service records, warranties, inspections, accident files, other)
 * accumulate rather than supersede.
 * <p>
 * If you change a singleton flag here, update the partial unique index in the migration too.
 */
public enum VehicleDocumentType {
    LICENCE_DISC(true, true),
    ROADWORTHY(true, true),
    INSURANCE(true, true),
    REGISTRATION_CERTIFICATE(true, false),
    PURCHASE_AGREEMENT(true, false),
    FINANCE_AGREEMENT(true, false),
    TRACKING_CERTIFICATE(true, false),
    SERVICE_RECORD(false, false),
    WARRANTY(false, false),
    INSPECTION(false, false),
    ACCIDENT(false, false),
    OTHER(false, false);

    private final boolean singleton;
    private final boolean tracksVehicleExpiry;

    VehicleDocumentType(boolean singleton, boolean tracksVehicleExpiry) {
        this.singleton = singleton;
        this.tracksVehicleExpiry = tracksVehicleExpiry;
    }

    public boolean isSingleton() { return singleton; }
    public boolean tracksVehicleExpiry() { return tracksVehicleExpiry; }
}