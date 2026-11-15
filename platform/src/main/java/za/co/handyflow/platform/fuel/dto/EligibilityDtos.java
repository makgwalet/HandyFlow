package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Eligibility: which tanker and driver may serve which site and product. A Block stops the allocation; a Review needs an authorised person and a reason. */
public final class EligibilityDtos {
    private EligibilityDtos() {}

    public enum Severity { BLOCK, REVIEW, INFO }

    /** code is stable for the screen and tests; message is plain words for the person. */
    public record Finding(String code, Severity severity, String message) {}

    public record EligibilityResult(List<Finding> findings) {
        public static final EligibilityResult NONE = new EligibilityResult(List.of());
        public List<Finding> blocks() { return findings.stream().filter(f -> f.severity() == Severity.BLOCK).toList(); }
        public List<Finding> reviews() { return findings.stream().filter(f -> f.severity() == Severity.REVIEW).toList(); }
        public List<Finding> info() { return findings.stream().filter(f -> f.severity() == Severity.INFO).toList(); }
        /** False when anything blocks. Review points do not block, but need an authorised person and a reason. */
        public boolean allowed() { return blocks().isEmpty(); }
    }

    /** Who is allocating and whether they hold the review permission. */
    public record ReviewAuthority(boolean canReview, UUID userId) {
        public static final ReviewAuthority NONE = new ReviewAuthority(false, null);
    }

    public record SiteRuleRequest(BigDecimal maxCapacityLitres, Integer maxGvmKg, BigDecimal maxLengthM, BigDecimal maxWidthM, BigDecimal maxHeightM,
                                  List<@Size(max = 40) String> approvedTypes, @Size(max = 500) String accessNotes, List<UUID> approvedTankerIds,
                                  boolean requireApprovedDrivers, @Size(max = 120) String requiredTraining) {}

    public record ApprovedTankerView(UUID tankerId, String registration) {}

    public record SiteRuleView(UUID siteId, boolean hasRule, BigDecimal maxCapacityLitres, Integer maxGvmKg, BigDecimal maxLengthM, BigDecimal maxWidthM,
                               BigDecimal maxHeightM, List<String> approvedTypes, String accessNotes, List<ApprovedTankerView> approvedTankers,
                               boolean requireApprovedDrivers, String requiredTraining) {}

    public record DriverApprovalRequest(String status, LocalDate inductionDate, LocalDate inductionExpiry, @Size(max = 120) String trainingRef,
                                        LocalDate trainingExpiry, @Size(max = 500) String conditions) {}

    /** state is what the check would say today: OK, NO_EXPIRY, EXPIRED, TRAINING_MISSING, TRAINING_EXPIRED, TRAINING_NO_EXPIRY, RESTRICTED or SUSPENDED. */
    public record DriverApprovalView(UUID employeeId, String driverName, String status, LocalDate inductionDate, LocalDate inductionExpiry, String trainingRef,
                                     LocalDate trainingExpiry, String conditions, String state) {}

    public record TankerSpecsRequest(@Size(max = 40) String tankerType, Integer gvmKg, BigDecimal lengthM, BigDecimal widthM, BigDecimal heightM) {}

    public record TankerSpecsView(UUID tankerId, String registration, String tankerType, Integer gvmKg, BigDecimal lengthM, BigDecimal widthM, BigDecimal heightM) {}

    // ── Compartment product rating, changeover and cleaning ──────────────────

    public record CompartmentRatingRequest(List<@Size(max = 30) String> fuelTypes) {}
    /** An empty list of products means the compartment is not restricted. */
    public record CompartmentRatingView(UUID compartmentId, int number, BigDecimal capacityLitres, List<String> fuelTypes, String lastCargo, java.time.Instant lastCargoAt, java.time.Instant lastCleanedAt) {}
    public record ChangeoverRuleRequest(@Size(max = 30) String fromFuel, @Size(max = 30) String toFuel, @Size(max = 300) String note) {}
    public record ChangeoverRuleView(UUID id, String fromFuel, String toFuel, String note) {}
    public record CleaningRequest(java.time.Instant cleanedAt, @Size(max = 120) String method, @Size(max = 120) String certificateRef, @Size(max = 300) String evidenceNote) {}
    public record CleaningView(UUID id, UUID compartmentId, java.time.Instant cleanedAt, String method, String certificateRef, String evidenceNote, UUID recordedBy) {}
    public record EligibilitySettings(boolean requireCompartmentRating) {}
}
