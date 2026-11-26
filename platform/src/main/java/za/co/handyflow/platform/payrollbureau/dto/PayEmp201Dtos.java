package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PayEmp201Dtos {

    private PayEmp201Dtos() {}

    public record EtiLineResponse(UUID employeeId, String employeeName, boolean eligible, String tier,
                                  BigDecimal remuneration, BigDecimal incentive, List<String> reasons) {}

    public record Emp201Response(UUID id, UUID clientId, LocalDate taxMonth, String status, int employeeCount,
                                 BigDecimal totalRemuneration, BigDecimal paye, BigDecimal uifEmployee, BigDecimal uifEmployer,
                                 BigDecimal sdl, BigDecimal etiCalculated, BigDecimal etiBroughtForward, BigDecimal etiUsed,
                                 BigDecimal etiCarriedForward, boolean etiClaimed, BigDecimal totalPayable, LocalDate dueDate,
                                 String runNumbers, Instant preparedAt, Instant filedAt, String filingReference,
                                 List<EtiLineResponse> etiLines) {}

    public record FileEmp201Request(String reference) {}

    public record EtiClaimRequest(boolean claimable, String note) {}

    public record EmployeeEtiRequest(String idKind, boolean domesticWorker, boolean connectedPerson,
                                     boolean belowMinimumWage, Integer monthsPrior) {}
}
