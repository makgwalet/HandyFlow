package za.co.handyflow.platform.fleet.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A driver's Safety &amp; Performance Score for a period. {@code overall} is the weighted average of the
 * components that had data; {@code coverage} is the share of configured weight that
 * had data, and {@code reliable} is true when that is at least 50% — treat a score with
 * low coverage as indicative only.
 */
public record DriverScoreResponse(
        Double overall,             // 0–100, one decimal; null when no component had data
        String band,                // EXCELLENT | GOOD | FAIR | POOR; null when overall is null
        double coverage,            // % of configured weight that had data
        boolean reliable,
        LocalDate periodFrom,
        LocalDate periodTo,
        List<ScoreComponentResponse> components
) {}