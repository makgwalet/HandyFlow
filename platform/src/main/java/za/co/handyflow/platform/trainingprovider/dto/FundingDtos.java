package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FundingDtos {
    private FundingDtos() {
    }

    public record FunderRequest(@NotBlank String name, @NotBlank String funderType, UUID clientId, String contactName, String contactEmail, String contactPhone, String note) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record LinkRequest(@NotNull UUID enrolmentId, @NotNull UUID funderId, @NotNull LocalDate fundedFrom, String agreementRef, String funderReference, String note) {
    }

    public record EndRequest(@NotNull LocalDate endedOn, @NotBlank String reason) {
    }

    public record FunderView(UUID id, String funderNo, String name, String funderType, UUID clientId, String clientName, String contactName, String contactEmail,
                             String contactPhone, String note, String status, String retireReason, long activeLinks) {
    }

    public record LinkView(UUID id, UUID enrolmentId, String enrolmentNo, String learnerName, UUID funderId, String funderNo, String funderName, String funderType,
                           LocalDate fundedFrom, String agreementRef, String funderReference, String note, String status, LocalDate endedOn, String endReason) {
    }

    public record EnrolmentFunding(List<LinkView> links, String summary) {
    }
}
