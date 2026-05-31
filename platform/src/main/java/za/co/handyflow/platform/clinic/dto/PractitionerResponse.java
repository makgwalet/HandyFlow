// src/main/java/za/co/handyflow/platform/clinic/dto/PractitionerResponse.java
package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public record PractitionerResponse(
        UUID id, String firstName, String lastName, String fullName,
        String specialty, String hpcsaNumber, String practiceNumber,
        String phone, String email, boolean active, Instant createdAt
) {}