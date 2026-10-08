package za.co.handyflow.platform.clinic.dto;

import java.util.List;

/** Which consultation stages a visit type requires before signing. */
public final class VisitStageDtos {
    private VisitStageDtos() {}

    public record Stage(String stage, boolean required) {}

    /** source: TENANT (this practice's own), PLATFORM (platform default) or DEFAULT (nothing set: Symptoms + Diagnosis). */
    public record Stages(String visitType, String source, List<Stage> stages) {}

    public record SetStagesRequest(List<Stage> stages) {}
}
