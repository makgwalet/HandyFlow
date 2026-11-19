package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FormDtos {
    private FormDtos() {}

    // ── Staff ──
    public record TopicRequest(@NotBlank @Size(max = 100) String name, @Size(max = 300) String description, Boolean visible) {}
    public record TopicResponse(UUID id, String name, String description, boolean visible, long optedOut, long usedByActiveCampaigns) {}

    public record FormRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 1000) String description,
            @Size(max = 40) String buttonLabel,
            @NotBlank @Size(min = 20, max = 1000) String consentText,
            @Size(max = 300) String successMessage,
            Boolean collectName,
            @Size(max = 20) List<UUID> topicIds) {}

    public record FormResponse(UUID id, String slug, String name, String title, String description, String buttonLabel,
                               String consentText, String successMessage, boolean collectName, List<UUID> topicIds,
                               String status, long requests, long confirmed, Instant createdAt) {}

    public record CampaignTopicRequest(UUID topicId) {}

    // ── Public ──
    public record PublicTopic(UUID id, String name, String description) {}
    public record PublicForm(String slug, String businessName, String title, String description, String buttonLabel,
                             String consentText, boolean collectName, List<PublicTopic> topics) {}
    public record SubmitRequest(@NotBlank @Size(max = 320) String email, @Size(max = 150) String name,
                                List<UUID> topicIds, boolean consent, @Size(max = 200) String website) {}
    public record ConfirmResult(String businessName, boolean alreadyConfirmed, String preferencesToken) {}

    public record PrefTopic(UUID id, String name, String description, boolean subscribed) {}
    public record PrefView(String businessName, String email, String name, boolean optedIn, List<PrefTopic> topics) {}
    public record PrefUpdate(List<UUID> topicIds) {}
}