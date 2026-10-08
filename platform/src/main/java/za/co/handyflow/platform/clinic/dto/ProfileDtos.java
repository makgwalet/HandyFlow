package za.co.handyflow.platform.clinic.dto;

import java.time.LocalDate;
import java.util.List;

/** The patient profile page (patch 0166). */
public final class ProfileDtos {
    private ProfileDtos() {}

    public record ProfileRequest(String title, String idType, String nationality, String preferredLanguage, String preferredContact,
                                 String addressLine1, String addressLine2, String suburb, String city, String province, String postalCode,
                                 String emergencyRelationship, String secondaryContactName, String secondaryContactPhone,
                                 String secondaryContactRelationship, String paymentType) {}

    /** One thing a complete profile has. {@code section} is where it is filled in: identity, contact, address, emergency, scheme, consent. */
    public record ChecklistItem(String key, String label, String section, boolean done) {}

    public record Completeness(int done, int total, int percent, List<ChecklistItem> items) {}

    public record ProfileResponse(String title, String idType, String nationality, String preferredLanguage, String preferredContact,
                                  String addressLine1, String addressLine2, String suburb, String city, String province, String postalCode,
                                  String emergencyRelationship, String secondaryContactName, String secondaryContactPhone,
                                  String secondaryContactRelationship, String paymentType, Completeness completeness) {}

    /** Name, identification and sex: the details a registration can get wrong. */
    public record DemographicsRequest(String firstName, String lastName, String idNumber, LocalDate dateOfBirth, String gender, String sexAtBirth) {}

    /** Phone, email and the first emergency contact. */
    public record ContactRequest(String phone, String email, String emergencyContactName, String emergencyContactPhone) {}

    public record PatientCore(java.util.UUID id, String firstName, String lastName, String idNumber, LocalDate dateOfBirth,
                              String gender, String sexAtBirth, String phone, String email,
                              String emergencyContactName, String emergencyContactPhone) {}
}
