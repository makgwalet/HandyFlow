package za.co.handyflow.platform.documents.application;

import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

/**
 * How a module tells the records retention engine about its records. Calls join the caller's transaction.
 * <ul>
 *   <li>{@link #track} when a record that holds files is created (the clock has not started: a live record is never due);</li>
 *   <li>{@link #startClock} when the record reaches the end of its active life (a project is closed, a document archived);</li>
 *   <li>{@link #moduleHoldChanged} when the module's own screen placed or lifted a legal hold, so both stay the same.</li>
 * </ul>
 * Disposal itself is never started by a module: it is proposed and approved by two people on the retention screen, and the engine
 * then calls the module's {@link RetentionProvider}.
 */
public interface RetentionFacade {

    /** Creates or refreshes the retention record of one source record. Ignored for one already disposed. */
    void track(TenantId tenantId, String moduleCode, String sourceType, UUID sourceId, String category,
               String reference, String title, String contextLabel);

    /** Starts the clock on the given records that have not started yet. Returns how many started. Unknown ids are ignored. */
    int startClock(TenantId tenantId, String moduleCode, String sourceType, Collection<UUID> sourceIds, LocalDate start);

    /** The module changed its own hold flag. Mirrors it here without calling the module back. Ignored for an untracked record. */
    void moduleHoldChanged(TenantId tenantId, String moduleCode, String sourceType, UUID sourceId, boolean hold, String reason,
                           UUID by, String byName);
}
