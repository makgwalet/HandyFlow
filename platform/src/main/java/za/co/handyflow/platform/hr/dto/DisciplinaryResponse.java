package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DisciplinaryResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        LocalDate incidentDate,
        String incidentType,
        String description,
        String outcome,
        LocalDate hearingDate,
        boolean acknowledged,
        Instant createdAt
) {}