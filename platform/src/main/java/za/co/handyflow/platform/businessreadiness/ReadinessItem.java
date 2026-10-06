package za.co.handyflow.platform.businessreadiness;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The verdict for one requirement.
 *
 * @param expiresOn                 when the binding piece of evidence stops being valid, if it does
 * @param expiringSoon              valid on the date that matters but expires within 30 days of it
 * @param differsFromManualStatus   the user's own tick and the evidence disagree (they said MET and it is not, or said MISSING and it is met)
 * @param newerVersionAvailable     a newer version of the tracked requirement exists than the one this was judged against
 */
public record ReadinessItem(UUID requirementId, String label, String manualStatus, ReadinessResult result, String detail, LocalDate expiresOn,
                            boolean expiringSoon, boolean differsFromManualStatus, boolean newerVersionAvailable) {}
