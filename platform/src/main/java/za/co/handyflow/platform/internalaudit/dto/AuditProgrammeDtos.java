package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuditProgrammeDtos {
    private AuditProgrammeDtos() {}

    public record ApplyRequest(List<String> domains, List<String> codes) {}
    public record RecordRequest(String status, String conclusion, String note) {}
    public record LinkRequest(UUID findingId) {}

    public record ControlView(String code, String domain, String area, String objective, String procedure, String reference) {}
    public record Library(Map<String, String> domains, List<ControlView> controls, String notice) {}
    public record StepView(String controlCode, String domain, String area, String objective, String procedure, String reference, String status, String conclusion,
                           String note, UUID findingId, boolean needsFinding, String updatedByName, Instant updatedAt) {}
    public record CountsView(int total, int completed, int notApplicable, int inProgress, int notStarted, int ineffective, int partial, int needingFinding, int donePct) {}
    public record Programme(UUID engagementId, CountsView overall, Map<String, CountsView> byDomain, List<StepView> steps) {}
}
