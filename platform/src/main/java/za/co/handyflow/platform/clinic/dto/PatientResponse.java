package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        String idNumber,
        LocalDate dateOfBirth,
        String gender,
        String phone,
        String email,
        String bloodType,
        List<String> allergies,
        List<String> chronicConditions,
        String emergencyContactName,
        String emergencyContactPhone,
        String notes,
        boolean active,
        Instant createdAt
) {}