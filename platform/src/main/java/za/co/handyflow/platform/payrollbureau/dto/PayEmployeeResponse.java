package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayEmployeeResponse(
        UUID id, String employeeNumber, String firstName, String lastName, String fullName,
        String idNumber, String taxNumber, LocalDate dateOfBirth, String email, String phone,
        BigDecimal grossSalary, BigDecimal travelAllowance,
        BigDecimal pensionContribution, BigDecimal medicalAidContribution,
        String bankName, String bankAccountNumber, String bankBranchCode,
        LocalDate startDate, LocalDate endDate, String status, Instant createdAt
) {}