package za.co.handyflow.platform.marketing.dto;

import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}

    /** Counts are recipients whose email was SENT in the window (cohort by sent date). Rates are 0..1 or null. */
    public record Period(long campaigns, long sent, long opened, long clicked, long unsubscribed,
                         Double openRate, Double clickRate, Double unsubscribeRate) {}

    public record DayPoint(String date, long sent, long opened, long clicked) {}

    public record SourceCount(String source, long count) {}

    public record Dashboard(int days, Period current, Period previous, List<DayPoint> series, List<SourceCount> sources) {}
}