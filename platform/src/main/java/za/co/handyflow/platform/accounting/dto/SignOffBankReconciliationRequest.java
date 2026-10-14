package za.co.handyflow.platform.accounting.dto;

import java.time.LocalDate;

public record SignOffBankReconciliationRequest(LocalDate periodEnd, String note) {}
