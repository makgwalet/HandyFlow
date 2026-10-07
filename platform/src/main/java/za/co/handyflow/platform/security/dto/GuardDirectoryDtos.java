// security/dto/GuardDirectoryDtos.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The guards list: one page of rows plus numbers for the whole filtered set, so the tiles and pills stay right past page one. */
public final class GuardDirectoryDtos {
    private GuardDirectoryDtos() {}

    /**
     * @param guard           the same record the guard endpoints return (the edit and view dialogs use it)
     * @param psiraState      EXPIRED, EXPIRING (30 days or less), VALID or NONE (no expiry date on file)
     * @param psiraDaysLeft   days until expiry, negative when expired, null when there is no date
     * @param screeningStatus the guard's screening roll-up: CLEARED, PENDING, FLAGGED or UNSCREENED
     * @param lastActivityAt  the later of the guard's last started shift and last checkpoint scan, null if neither
     */
    public record Row(GuardResponse guard, String psiraState, Integer psiraDaysLeft, String screeningStatus, Instant lastActivityAt) {}

    /** Counts over everything that matches the search, grade, branch and compliance filters, ignoring the status filter. */
    public record Counts(long total, Map<String, Long> byStatus, long psiraExpired, long psiraExpiring,
                         long screeningFlagged, long screeningPending, long screeningUnscreened) {}

    public record Result(List<Row> rows, long totalElements, int page, int size, Counts counts) {}
}
