package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ServiceCatalogueDtos {

    private ServiceCatalogueDtos() {}

    public record ItemInput(@NotBlank @Size(max = 12) String kind, @NotBlank @Size(max = 300) String text, boolean mandatory) {}

    public record SaveVersionRequest(
            @Size(max = 255) String name, @Size(max = 20) String category, @Size(max = 4000) String description,
            @Size(max = 2000) String applicabilityNote, @Size(max = 2000) String exclusions, BigDecimal estimatedHours, Integer slaDays,
            @Size(max = 20) String responsibleRole, Boolean requiresReview, @Size(max = 20) String pricingModel, @Size(max = 20) String billingTrigger,
            @Size(max = 2000) String changeSummary, @Valid List<ItemInput> items) {}

    public record CreateServiceRequest(@NotBlank @Size(max = 60) String code, @Valid SaveVersionRequest version) {}

    public record ApproveRequest(LocalDate effectiveFrom, @Size(max = 1000) String exceptionReason, @Size(max = 2000) String note) {}

    public record RejectRequest(@Size(max = 2000) String reason) {}

    public record RetireRequest(LocalDate on) {}

    public record ItemView(String kind, String text, boolean mandatory) {}

    public record VersionView(UUID id, int versionNumber, String reviewStatus, String name, String category, String description, String applicabilityNote,
                              String exclusions, BigDecimal estimatedHours, Integer slaDays, String responsibleRole, boolean requiresReview,
                              String pricingModel, String billingTrigger, String changeSummary, UUID authoredBy, UUID submittedBy, Instant submittedAt,
                              UUID reviewedBy, Instant reviewedAt, String reviewNote, String reviewExceptionReason, LocalDate effectiveFrom,
                              LocalDate effectiveTo, boolean inForce, List<ItemView> items) {}

    public record ServiceSummary(UUID id, String code, String name, String category, Integer inForceVersion, int latestVersion, String latestStatus,
                                 String pricingModel, BigDecimal estimatedHours) {}

    public record ServiceDetail(UUID id, String code, List<VersionView> versions) {}
}
