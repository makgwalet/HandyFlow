package za.co.handyflow.platform.notifications.dto;

public record NotificationPreferenceResponse(
        String channel,   // EMAIL | SMS  (IN_APP is not user-configurable)
        boolean enabled
) {}