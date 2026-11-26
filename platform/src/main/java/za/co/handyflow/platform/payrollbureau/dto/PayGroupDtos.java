package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public final class PayGroupDtos {
    private PayGroupDtos() {}

    public record CreatePayGroupRequest(@NotBlank @Size(max = 100) String name, @NotBlank String frequency,
                                        LocalDate anchorDate, Integer payLagDays) {}

    public record PayGroupResponse(UUID id, String name, String frequency, LocalDate anchorDate, int payLagDays,
                                   String status, int employeeCount) {}

    public record AssignPayGroupRequest(UUID payGroupId) {}

    public record NextPeriodResponse(LocalDate periodStart, LocalDate periodEnd, LocalDate payDate, int taxYear) {}
}
