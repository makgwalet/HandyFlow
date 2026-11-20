package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.util.List;

public record KycSettingsResponse(boolean enforce, List<Change> history) {
    public record Change(boolean enforce, String reason, String changedByName, Instant changedAt) { }
}
