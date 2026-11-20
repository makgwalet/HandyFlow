package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TrustReconciliationSummary(UUID id, LocalDate periodEnd, String status, BigDecimal statementBalance, BigDecimal difference,
                                         String preparedByName, Instant preparedAt, String approvedByName, Instant approvedAt) {}
