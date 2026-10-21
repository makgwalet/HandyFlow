package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HR letters: what the screens read and what they send. */
public final class LetterDtos {

    private LetterDtos() {}

    public record FieldView(String key, String label, String group) {}

    public record TemplateView(UUID id, String code, String name, String category, String subject, String body, boolean requiresAck, boolean active,
                               boolean customised, boolean standard, int revision, List<String> fields) {}

    /** The centre's templates plus the merge fields a template may use. */
    public record Templates(List<TemplateView> templates, List<FieldView> fields) {}

    public record Row(UUID id, String number, String subject, String templateName, UUID employeeId, String employeeName, UUID caseId,
                      String caseNumber, String status, LocalDate issuedOn, boolean requiresAck, Instant acknowledgedAt, Instant createdAt) {}

    public record EventView(String action, String note, String doneBy, Instant doneAt) {}

    public record Detail(Row row, String body, List<String> gaps, List<EventView> events, List<String> actions, String fileName, String fileSha256,
                         String voidReason, int templateRevision) {}

    /** What the employee sees of a letter given to them. */
    public record Mine(UUID id, String number, String subject, String body, String templateName, LocalDate issuedOn, boolean requiresAck,
                       Instant acknowledgedAt, boolean canAcknowledge) {}

    public record PrepareRequest(UUID templateId, UUID employeeId, UUID caseId) {}

    public record DraftRequest(String subject, String body) {}

    public record VoidRequest(String reason) {}

    public record TemplateRequest(String name, String category, String subject, String body, Boolean requiresAck, Boolean active) {}
}
