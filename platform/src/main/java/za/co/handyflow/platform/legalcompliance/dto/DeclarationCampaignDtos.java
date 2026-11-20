package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.CampaignStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationType;
import za.co.handyflow.platform.legalcompliance.domain.model.ReturnStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Staff-wide declaration campaigns. Managers see who answered what, never the content of a declaration. */
public final class DeclarationCampaignDtos {

    private DeclarationCampaignDtos() {}

    public record CampaignRequest(@NotBlank @Size(max = 255) String title, @Size(max = 4000) String instructions, @NotNull LocalDate dueDate, @Size(max = 100) String department) {}

    public record CloseRequest(@NotBlank @Size(max = 2000) String reason) {}

    public enum Answer { NIL, UNCHANGED }

    public record AnswerRequest(@NotNull Answer answer) {}

    public record CampaignResponse(UUID id, String reference, String title, String instructions, LocalDate dueDate, boolean overdue, String department, CampaignStatus status, String launchedByName,
                                   Instant closedAt, String closeReason, int total, int nil, int unchanged, int declared, int pending, int left, int percentComplete) {}

    public record SyncResult(int added, int excused, int reinstated, CampaignResponse campaign) {}

    public record ReturnRow(UUID id, String userName, String userEmail, ReturnStatus status, Instant respondedAt) {}

    public record CampaignDetail(CampaignResponse campaign, List<ReturnRow> returns) {}

    /** The person's own declaration made through the campaign. */
    public record MyDeclaration(UUID id, String reference, DeclarationType declarationType, String counterparty, DeclarationStatus status) {}

    public record MyReturn(UUID id, UUID campaignId, String campaignTitle, String instructions, LocalDate dueDate, boolean overdue, boolean campaignOpen, ReturnStatus status, Instant respondedAt,
                           String statement, int existingDeclarations, List<MyDeclaration> declarations, String nilStatement, String unchangedStatement,
                           int reviewDatesExtended) {
        /** Only an answer of "unchanged" reports how many review dates it extended; every other view says 0. */
        public MyReturn withReviewDatesExtended(int n) {
            return new MyReturn(id, campaignId, campaignTitle, instructions, dueDate, overdue, campaignOpen, status, respondedAt, statement, existingDeclarations, declarations, nilStatement, unchangedStatement, n);
        }
    }
}
