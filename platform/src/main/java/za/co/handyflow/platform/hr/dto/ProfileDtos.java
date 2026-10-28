package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Next of kin, dependants and the career timeline. */
public final class ProfileDtos {

    private ProfileDtos() {}

    public record RelativeRequest(String kind, String fullName, String relationship, String phone, String email, LocalDate dateOfBirth, String address,
                                  Boolean beneficiary, BigDecimal beneficiaryPercent, String notes) {}

    public record RelativeView(UUID id, UUID employeeId, String kind, String fullName, String relationship, String relationshipLabel, String phone, String email,
                               LocalDate dateOfBirth, Integer age, String address, boolean beneficiary, BigDecimal beneficiaryPercent, String notes, Instant updatedAt) {}

    public record CareerRequest(String kind, LocalDate eventDate, LocalDate endDate, String employer, String jobTitle, String department, String note) {}

    public record CareerView(UUID id, String kind, String kindLabel, LocalDate eventDate, LocalDate endDate, String employer, String jobTitle, String department,
                             String fromValue, String toValue, BigDecimal percentChange, String note, boolean payRelated, boolean auto, Instant createdAt) {}
}
