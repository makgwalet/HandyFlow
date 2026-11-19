package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingFormService;
import za.co.handyflow.platform.marketing.application.internal.MarketingService;
import za.co.handyflow.platform.marketing.dto.CampaignResponse;
import za.co.handyflow.platform.marketing.dto.FormDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketing")
@RequiredArgsConstructor
@Tag(name = "Marketing Forms & Topics")
public class MarketingFormController {

    private final MarketingFormService forms;
    private final MarketingService     marketing;

    @GetMapping("/topics")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<List<TopicResponse>>> topics() {
        return ResponseEntity.ok(ApiResponse.success(forms.topics(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/topics")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<TopicResponse>> createTopic(@Valid @RequestBody TopicRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Topic created",
                forms.createTopic(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/topics/{id}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<TopicResponse>> updateTopic(@PathVariable UUID id, @Valid @RequestBody TopicRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Topic updated", forms.updateTopic(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/topics/{id}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Delete a topic (409 while an unfinished campaign uses it)")
    public ResponseEntity<ApiResponse<Void>> deleteTopic(@PathVariable UUID id) {
        forms.deleteTopic(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Topic deleted", null));
    }

    @GetMapping("/forms")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    public ResponseEntity<ApiResponse<List<FormResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(forms.forms(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/forms")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<FormResponse>> create(@Valid @RequestBody FormRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Form created",
                forms.createForm(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/forms/{id}")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<FormResponse>> update(@PathVariable UUID id, @Valid @RequestBody FormRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Form updated", forms.updateForm(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/forms/{id}/enable")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<FormResponse>> enable(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Form enabled", forms.setFormStatus(TenantContext.getTenantIdAsObject(), id, "ACTIVE")));
    }

    @PostMapping("/forms/{id}/disable")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    public ResponseEntity<ApiResponse<FormResponse>> disable(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Form disabled", forms.setFormStatus(TenantContext.getTenantIdAsObject(), id, "DISABLED")));
    }

    @PutMapping("/campaigns/{id}/topic")
    @PreAuthorize("hasAuthority('MARKETING_MANAGE')")
    @Operation(summary = "Set or clear a DRAFT/SCHEDULED campaign's topic. Voids any approval if it changes.")
    public ResponseEntity<ApiResponse<CampaignResponse>> setCampaignTopic(@PathVariable UUID id, @RequestBody CampaignTopicRequest req) {
        var tenant = TenantContext.getTenantIdAsObject();
        forms.setCampaignTopic(tenant, id, req.topicId());
        return ResponseEntity.ok(ApiResponse.success("Topic saved", marketing.getCampaign(tenant, id)));
    }
}