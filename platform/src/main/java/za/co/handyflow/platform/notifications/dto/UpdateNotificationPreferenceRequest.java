package za.co.handyflow.platform.notifications.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateNotificationPreferenceRequest(
        @NotNull @Pattern(regexp = "EMAIL|SMS", message = "channel must be EMAIL or SMS")
        String channel,
        @NotNull
        Boolean enabled
) {}