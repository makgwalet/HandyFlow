package za.co.handyflow.platform.recruiter.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecruiterDashboardDtos {
    private RecruiterDashboardDtos() {}

    /** A figure for the period, the same figure for the period before it, and the change. Value is null when there is nothing to measure. */
    public record Metric(Double value, Double previous, Double changePercent) {}
    public record StageCount(String stage, long count) {}
    public record TopJob(UUID id, String title, long applications, long hires) {}
    public record SourceCount(String source, long count) {}
    public record UpcomingInterview(UUID applicationId, String candidate, String jobTitle, String type, Instant scheduledAt) {}
    public record Activity(Instant at, String kind, UUID applicationId, String candidate, String jobTitle, String stage) {}
    public record Dashboard(int days, long activeJobs, Metric applications, Metric interviews, Metric offers, Metric hires, Metric timeToHireDays,
                            List<StageCount> funnel, List<TopJob> topJobs, List<SourceCount> sources,
                            List<UpcomingInterview> upcoming, List<Activity> activity) {}
}
