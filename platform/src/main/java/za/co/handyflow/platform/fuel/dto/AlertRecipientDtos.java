package za.co.handyflow.platform.fuel.dto;

import java.util.List;
import java.util.UUID;

public final class AlertRecipientDtos {
    private AlertRecipientDtos() {}

    /** The extra people told about one kind of alert. Administrators are told as well unless excludeAdmins is set (only allowed when someone else is chosen), so they are not listed. */
    public record Topic(String topic, String label, List<UUID> userIds, boolean excludeAdmins) {
        public Topic(String topic, String label, List<UUID> userIds) { this(topic, label, userIds, false); }
    }

    /** excludeAdmins left out (null) keeps the current setting. */
    public record SaveRequest(List<UUID> userIds, Boolean excludeAdmins) {
        public SaveRequest(List<UUID> userIds) { this(userIds, null); }
    }
}
