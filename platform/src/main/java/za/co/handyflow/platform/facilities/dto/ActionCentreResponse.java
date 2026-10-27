package za.co.handyflow.platform.facilities.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One ranked list of everything that needs attention. Severity: CRITICAL (a lapsed certificate, a critical failure
 * not yet put right, an action overdue by more than a week), HIGH (other overdue actions, certificates expiring
 * within 14 days), MEDIUM (certificates expiring within the warning window).
 */
public record ActionCentreResponse(
        long critical, long high, long medium, List<Item> items
) {
    public record Item(String severity, String type, UUID siteId, UUID assetId, String title, String detail,
                       LocalDate dueDate, String entityType, UUID entityId) {}
}
