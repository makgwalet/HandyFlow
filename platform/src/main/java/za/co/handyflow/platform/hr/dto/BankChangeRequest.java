package za.co.handyflow.platform.hr.dto;

import java.time.LocalDate;

public record BankChangeRequest(String bankName, String accountNumber, String branchCode, LocalDate effectiveFrom, String reason) {}
