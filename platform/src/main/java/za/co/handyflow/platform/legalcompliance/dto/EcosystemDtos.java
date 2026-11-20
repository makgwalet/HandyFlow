package za.co.handyflow.platform.legalcompliance.dto;

import java.time.Instant;
import java.util.List;

/** The cross-module view. Counts only; each module still owns its data. */
public final class EcosystemDtos {

    private EcosystemDtos() {}

    public record Signal(String key, String label, long count, String severity, String actionUrl) {}

    /** status is OK, or UNAVAILABLE when the module's check failed (the rest of the view still loads). */
    public record ModuleView(String moduleKey, String moduleName, String status, List<Signal> signals) {}

    public record Ecosystem(Instant generatedAt, List<ModuleView> modules, long critical, long warnings) {}
}
