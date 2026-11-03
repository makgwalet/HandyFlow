package za.co.handyflow.platform.projects.dto;

import java.util.List;

/**
 * A dependency is saved even when the CURRENT planned dates break it — that is a warning
 * (the planner may fix the dates next), not an error. Cycles and invalid links ARE rejected.
 */
public record DependencyCreatedResponse(DependencyResponse dependency, List<String> warnings) {}