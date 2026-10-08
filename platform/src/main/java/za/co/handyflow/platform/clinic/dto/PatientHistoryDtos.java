package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

/** Family history, lifestyle and social history, and medical aid on a patient's file (patch 0158). */
public final class PatientHistoryDtos {
    private PatientHistoryDtos() {}

    public record FamilyHistoryRequest(String relative, String conditionName, Integer ageAtOnset, Boolean deceased,
                                       String notes, String status) {}

    public record FamilyHistoryResponse(UUID id, UUID patientId, String relative, String conditionName, Integer ageAtOnset,
                                        Boolean deceased, String notes, String status, Instant createdAt) {}

    public record SocialHistoryRequest(String smokingStatus, String alcoholUse, String substanceUse, String occupation,
                                       String livingSituation, String physicalActivity, String notes) {}

    /** {@code recorded} is false when nothing has been entered yet; the values are then all UNKNOWN or empty. */
    public record SocialHistoryResponse(boolean recorded, String smokingStatus, String alcoholUse, String substanceUse,
                                        String occupation, String livingSituation, String physicalActivity, String notes,
                                        Instant updatedAt) {}

    public record MedicalAidRequest(String schemeName, String planName, String memberNumber, String dependentCode,
                                    String principalMember, String schemeContactPhone) {}

    /** {@code inherited} is true when a dependant has none of their own and the principal's is shown. */
    public record MedicalAidResponse(UUID id, String schemeName, String planName, String memberNumber, String dependentCode,
                                     String principalMember, String schemeContactPhone, boolean inherited, String inheritedFrom) {}
}
