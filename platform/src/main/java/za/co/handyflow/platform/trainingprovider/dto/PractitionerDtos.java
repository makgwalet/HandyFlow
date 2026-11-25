package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PractitionerDtos {

    private PractitionerDtos() {
    }

    public record PractitionerRequest(@NotBlank String firstNames, @NotBlank String surname, String email, String phone,
                                      @NotBlank String employmentType, UUID userId, String notes) {
    }

    /** verifiedRoles: roles for which the person has an appointment that is checked and in force today. */
    public record PractitionerView(UUID id, String practitionerNo, String firstNames, String surname, String email, String phone,
                                   String employmentType, UUID userId, String notes, String status, String inactiveReason,
                                   long liveAppointments, List<String> verifiedRoles) {
    }

    public record PractitionerReason(@NotBlank String reason) {
    }

    public record AppointmentRequest(@NotBlank String role, @NotNull UUID programmeId, UUID authorityId, String credentialRef,
                                     String credentialNote, LocalDate validFrom, LocalDate validTo) {
    }

    public record AppointmentChange(UUID authorityId, String credentialRef, String credentialNote, LocalDate validFrom, LocalDate validTo) {
    }

    public record VerifyRequest(@NotBlank String verificationSource, @NotNull LocalDate verifiedOn, String overrideReason) {
    }

    public record AppointmentEvent(UUID id, String action, String note, UUID actedBy, Instant actedAt) {
    }

    public record AppointmentView(UUID id, UUID practitionerId, String role, UUID programmeId, String programmeTitle, UUID authorityId,
                                  String authorityName, String credentialRef, String credentialNote, LocalDate validFrom, LocalDate validTo,
                                  String verificationSource, LocalDate verifiedOn, String status, String withdrawnReason, String standing,
                                  String standingNote, List<AppointmentEvent> history) {
    }

    public record PractitionerDetail(PractitionerView practitioner, List<AppointmentView> appointments) {
    }

    /** Someone with a checked, in-force appointment for the role on the programme on the day asked. */
    public record EligibleView(UUID practitionerId, String practitionerNo, String name, String role, UUID appointmentId, LocalDate verifiedOn) {
    }
}
