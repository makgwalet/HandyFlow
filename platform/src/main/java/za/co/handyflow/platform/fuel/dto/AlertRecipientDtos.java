package za.co.handyflow.platform.fuel.dto;

import java.util.List;
import java.util.UUID;

public final class AlertRecipientDtos {
    private AlertRecipientDtos() {}

    /** The extra people told about one kind of alert. Administrators are always told as well, so they are not listed. */
    public record Topic(String topic, String label, List<UUID> userIds) {}

    public record SaveRequest(List<UUID> userIds) {}
}
