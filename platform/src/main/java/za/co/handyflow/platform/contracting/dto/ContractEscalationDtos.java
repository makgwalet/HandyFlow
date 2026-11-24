package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.contracting.application.internal.ApprovalEscalationService.SlaReport;
import za.co.handyflow.platform.contracting.domain.model.ApprovalSla;
import za.co.handyflow.platform.contracting.domain.model.ContractApproverAbsence;
import za.co.handyflow.platform.contracting.domain.model.ContractClosureDay;
import za.co.handyflow.platform.contracting.domain.model.ContractEscalationSettings;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ContractEscalationDtos {
    private ContractEscalationDtos() {}

    public record SettingsView(boolean enabled, Instant enabledSince, int reminder1Hours, int reminder2Hours, int escalateHours, String clockMode,
                               LocalTime workStart, LocalTime workEnd, List<DayOfWeek> workDays, boolean skipPublicHolidays,
                               List<UUID> escalationUserIds, Instant updatedAt) {
        public static SettingsView of(ContractEscalationSettings s) {
            return new SettingsView(s.isEnabled(), s.getEnabledSince(), s.getReminder1Hours(), s.getReminder2Hours(), s.getEscalateHours(), s.getClockMode(),
                    s.getWorkStart(), s.getWorkEnd(), s.workDaySet().stream().sorted().toList(), s.isSkipPublicHolidays(), s.escalationUsers(), s.getUpdatedAt());
        }
    }

    public record SettingsRequest(boolean enabled,
                                  @Min(1) @Max(720) int reminder1Hours, @Min(1) @Max(720) int reminder2Hours, @Min(1) @Max(720) int escalateHours,
                                  @NotNull @Size(max = 20) String clockMode, LocalTime workStart, LocalTime workEnd, List<DayOfWeek> workDays,
                                  boolean skipPublicHolidays, @Size(max = 20) List<UUID> escalationUserIds) {}

    public record ClosureView(UUID id, LocalDate day, String reason) {
        public static ClosureView of(ContractClosureDay c) { return new ClosureView(c.getId(), c.getClosedOn(), c.getReason()); }
    }

    public record ClosureRequest(@NotNull LocalDate day, @Size(max = 200) String reason) {}

    public record AbsenceView(UUID id, UUID userId, LocalDate from, LocalDate to, UUID delegateUserId, String reason) {
        public static AbsenceView of(ContractApproverAbsence a) {
            return new AbsenceView(a.getId(), a.getUserId(), a.getFromDate(), a.getToDate(), a.getDelegateUserId(), a.getReason());
        }
    }

    /** {@code userId} empty means the person making the request. */
    public record AbsenceRequest(UUID userId, @NotNull LocalDate from, @NotNull LocalDate to, UUID delegateUserId, @Size(max = 200) String reason) {}

    /** {@code userId} empty hands the request back to everyone who can approve it. */
    public record ReassignRequest(UUID userId, @Size(max = 500) String note) {}

    public record SlaView(Instant from, Instant to, int targetHours, boolean workingClock, ApprovalSla.Result result,
                          List<ApproverName> approvers) {
        public static SlaView of(SlaReport r) {
            return new SlaView(r.from(), r.to(), r.targetHours(), r.workingClock(), r.result(),
                    r.approverNames().entrySet().stream().map(e -> new ApproverName(e.getKey(), e.getValue())).toList());
        }
    }

    public record ApproverName(String userId, String name) {}
}
