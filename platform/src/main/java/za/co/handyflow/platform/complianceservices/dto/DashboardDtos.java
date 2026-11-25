package za.co.handyflow.platform.complianceservices.dto;

import za.co.handyflow.platform.complianceservices.dto.WorkQueueDtos.Counts;
import za.co.handyflow.platform.complianceservices.dto.WorkQueueDtos.QueueEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {}

    public record Kpis(int activeClients, int overdueDeadlines, int deadlinesNext14, int documentsToReview, int documentsExpiring, int openCases, int regulatoryOpen,
                       int quotesOut, BigDecimal quotesOutValue, int activeAgreements, int billsToRaise, BigDecimal billsToRaiseValue) {}

    public record Slice(String status, int count) {}

    public record Stage(String status, int count, BigDecimal value) {}

    public record Week(LocalDate start, int count) {}

    public record Month(String month, BigDecimal amount, int count) {}

    public record Upcoming(UUID clientId, String clientName, String title, LocalDate dueDate) {}

    public record Summary(LocalDate generatedOn, Kpis kpis, Counts queue, List<Slice> clientsByStatus, List<Stage> quotePipeline, List<Stage> tenderPipeline,
                          List<Week> deadlineWeeks, int deadlinesOverdue, List<Month> billingForecast, BigDecimal billingBehind, int billingBehindCount,
                          List<QueueEntry> urgent, List<Upcoming> upcoming) {}
}
