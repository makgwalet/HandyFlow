package za.co.handyflow.platform.fleet.domain.model;

/**
 * Kinds of document in a driver's file.
 * <ul>
 *   <li><b>singleton</b> — one current document of this type; a new upload supersedes the old (kept as history).</li>
 *   <li>Training certificates and "other" accumulate: a driver has many courses.</li>
 * </ul>
 * If you change a singleton flag here, update the partial unique index in V465 too.
 */
public enum DriverDocumentType {
    PHOTO(true),
    DRIVING_LICENCE(true),
    PRDP(true),
    MEDICAL_CERTIFICATE(true),
    ID_COPY(true),
    CONTRACT(true),
    TRAINING_CERTIFICATE(false),
    OTHER(false);

    private final boolean singleton;

    DriverDocumentType(boolean singleton) { this.singleton = singleton; }

    public boolean isSingleton() { return singleton; }
}
