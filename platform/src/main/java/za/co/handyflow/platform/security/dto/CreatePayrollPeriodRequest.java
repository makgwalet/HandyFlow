package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record CreatePayrollPeriodRequest(
        @NotBlank String name,
        @NotBlank String periodType,       // WEEKLY | BIWEEKLY | MONTHLY
        @NotNull  LocalDate periodStart,
        @NotNull  LocalDate periodEnd,
        UUID branchId                       // null = all branches
) {}
