package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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
                                  List<@Size(max = 40) String> approvedTypes, @Size(max = 500) String accessNotes, List<UUID> approvedTankerIds) {}

    public record ApprovedTankerView(UUID tankerId, String registration) {}

    public record SiteRuleView(UUID siteId, boolean hasRule, BigDecimal maxCapacityLitres, Integer maxGvmKg, BigDecimal maxLengthM, BigDecimal maxWidthM,
                               BigDecimal maxHeightM, List<String> approvedTypes, String accessNotes, List<ApprovedTankerView> approvedTankers) {}

    public record TankerSpecsRequest(@Size(max = 40) String tankerType, Integer gvmKg, BigDecimal lengthM, BigDecimal widthM, BigDecimal heightM) {}

    public record TankerSpecsView(UUID tankerId, String registration, String tankerType, Integer gvmKg, BigDecimal lengthM, BigDecimal widthM, BigDecimal heightM) {}
}
