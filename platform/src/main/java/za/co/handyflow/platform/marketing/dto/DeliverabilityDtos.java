package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class DeliverabilityDtos {
    private DeliverabilityDtos() {}

    /** status: PASS | WARN | FAIL | INFO */
    public record Check(String id, String status, String title, String detail, String fix) {}

    public record Result(String domain, Instant checkedAt, List<String> selectors, List<Check> checks,
                         int pass, int warn, int fail) {}

    public record State(String domain, List<String> selectors, Result result) {}

    public record SettingsRequest(@Size(max = 255) String domain, @Size(max = 5) List<@Size(max = 63) String> selectors) {}
}