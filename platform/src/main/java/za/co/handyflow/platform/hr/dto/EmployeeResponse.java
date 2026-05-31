package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String employeeNumber,
        String firstName,
        String lastName,
        String fullName,
        String idNumber,
        String taxNumber,
        LocalDate dateOfBirth,
        String gender,
        String race,
        String email,
        String phone,
        String employmentType,
        String jobTitle,
        String department,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        BigDecimal grossSalary,
        String payFrequency,
        BigDecimal medicalAidContribution,
        BigDecimal pensionContribution,
        BigDecimal travelAllowance,
        String emergencyContactName,
        String emergencyContactPhone,
        Instant createdAt
) {}