package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Request and response shapes for pay components (phase 1, slice 1e). */
public final class PayComponentDtos {

    private PayComponentDtos() {}

    public record PayComponentResponse(UUID id, String code, String name, String role, String sarsCode, boolean standard, String status) {}

    public record CreatePayComponentRequest(String code, String name, String role, String sarsCode) {}

    /** An amount an employee gets or has taken every pay period of their group. */
    public record EmployeeComponentResponse(UUID id, UUID componentId, String code, String name, String role, BigDecimal amount) {}

    public record SetEmployeeComponentRequest(@NotNull UUID componentId, BigDecimal amount) {}

    /** An amount for one employee in one pay run. */
    public record RunItemResponse(UUID id, UUID payEmployeeId, UUID componentId, String code, String name, String role,
                                  BigDecimal amount, String note) {}

    public record SetRunItemRequest(@NotNull UUID employeeId, @NotNull UUID componentId, BigDecimal amount, String note) {}

    public record PayslipLineResponse(String code, String name, String role, String sarsCode, String source, BigDecimal amount, String note) {}
}
