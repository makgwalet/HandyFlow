package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public final class DocumentDtos {
    private DocumentDtos() {
    }

    public record IssueRequest(@NotNull UUID enrolmentId, @NotBlank String docType) {
    }

    public record HandOverRequest(@NotNull LocalDate handedOverOn, @NotBlank String handedOverTo, @NotBlank String method) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record DocumentView(UUID id, String documentNo, String docType, UUID enrolmentId, String enrolmentNo, UUID learnerId, String learnerName, String programmeTitle,
                               Integer versionNo, LocalDate issuedOn, String title, String body, String disclaimer, String status, String voidReason, LocalDate handedOverOn,
                               String handedOverTo, String handedOverMethod, boolean completionStillStands) {
    }
}
