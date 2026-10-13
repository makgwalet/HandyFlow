package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationOptOutService;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationPolicy.ChannelStatus;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationService;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationService.SendCommand;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationService.SendOutcome;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationService.TimelineEntry;
import za.co.handyflow.platform.debtcollection.application.internal.CommunicationTemplateService;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationStatus;
import za.co.handyflow.platform.debtcollection.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Case communications, the unified timeline, message templates and the do-not-contact register.
 * Sending returns 201 whenever a record was created — including BLOCKED (policy refusal, with reason and
 * alternative channels) and FAILED (e.g. no sender configured); inspect "status" in the response.
 * Publishing templates and lifting opt-outs are supervisor actions (DEBTCOLLECTION_ADMIN).
 */
@RestController
@RequestMapping("/api/v1/debtcollection")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Communications", description = "Policy-gated messaging, timeline, templates and opt-outs")
public class CommunicationController {

    private final CommunicationService communicationService;
    private final CommunicationTemplateService templateService;
    private final CommunicationOptOutService optOutService;
    private final FeatureGuard featureGuard;

    @GetMapping("/communication-channels")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Which channels are usable, and why not (e.g. WhatsApp gate)")
    public ResponseEntity<ApiResponse<List<ChannelStatus>>> channels() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(communicationService.channelStatuses()));
    }

    // ── case messages ────────────────────────────────────────────────────────

    @PostMapping("/cases/{caseId}/communications")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Send an SMS/email to the debtor through the policy gate")
    public ResponseEntity<ApiResponse<CommunicationResponse>> send(@PathVariable UUID caseId,
                                                                   @Valid @RequestBody SendCommunicationRequest req) {
        featureGuard.requireModule("debtcollection");
        SendOutcome o = communicationService.send(TenantContext.getTenantIdAsObject(), caseId,
                new SendCommand(req.channel(), req.purpose(), req.recipient(), req.templateCode(), req.subject(),
                        req.body(), req.variables(), req.processingBasis(), req.idempotencyKey()),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        String msg = switch (o.communication().getStatus()) {
            case BLOCKED -> "Message blocked by policy";
            case FAILED -> "Message could not be sent";
            default -> "Message accepted";
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(msg,
                CommunicationResponse.of(o.communication(), o.suggestedAlternatives())));
    }

    @PostMapping("/cases/{caseId}/communications/inbound")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record a debtor's reply; opt-out keywords register an opt-out; raises a review work item")
    public ResponseEntity<ApiResponse<CommunicationResponse>> inbound(@PathVariable UUID caseId,
                                                                      @Valid @RequestBody RecordInboundRequest req) {
        featureGuard.requireModule("debtcollection");
        var c = communicationService.recordInbound(TenantContext.getTenantIdAsObject(), caseId, req.channel(), req.from(),
                req.body(), req.receivedAt(), req.providerMessageId(), TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Reply recorded", CommunicationResponse.of(c)));
    }

    @PostMapping("/cases/{caseId}/communications/letters")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record a letter dispatched by staff (post, registered post, courier, hand delivery)")
    public ResponseEntity<ApiResponse<CommunicationResponse>> letter(@PathVariable UUID caseId,
                                                                     @Valid @RequestBody RecordLetterRequest req) {
        featureGuard.requireModule("debtcollection");
        var c = communicationService.recordLetter(TenantContext.getTenantIdAsObject(), caseId, req.purpose(),
                req.postalAddress(), req.subject(), req.summary(), req.dispatchMethod(), req.trackingReference(),
                req.evidenceId(), req.sentAt(), req.templateCode(), TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Letter recorded", CommunicationResponse.of(c)));
    }

    @PostMapping("/communications/{id}/delivery-status")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Mark a letter DELIVERED or RETURNED (e.g. uncollected registered post)")
    public ResponseEntity<ApiResponse<CommunicationResponse>> letterStatus(@PathVariable UUID id,
                                                                           @Valid @RequestBody UpdateDeliveryStatusRequest req) {
        featureGuard.requireModule("debtcollection");
        var c = communicationService.updateLetterStatus(TenantContext.getTenantIdAsObject(), id, req.status(), req.detail());
        return ResponseEntity.ok(ApiResponse.success("Status updated", CommunicationResponse.of(c)));
    }

    @GetMapping("/cases/{caseId}/communications")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<CommunicationResponse>>> list(@PathVariable UUID caseId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                communicationService.listForCase(TenantContext.getTenantIdAsObject(), caseId).stream()
                        .map(CommunicationResponse::of).toList()));
    }

    @GetMapping("/cases/{caseId}/timeline")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Unified case timeline, newest first: messages, calls/contact log, PTP events")
    public ResponseEntity<ApiResponse<List<TimelineEntry>>> timeline(@PathVariable UUID caseId,
                                                                     @RequestParam(defaultValue = "100") int limit) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                communicationService.timeline(TenantContext.getTenantIdAsObject(), caseId, limit)));
    }

    // ── templates ────────────────────────────────────────────────────────────

    @PostMapping("/communication-templates")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Publish a template; an existing code gets its next version")
    public ResponseEntity<ApiResponse<TemplateResponse>> publishTemplate(@Valid @RequestBody PublishTemplateRequest req) {
        featureGuard.requireModule("debtcollection");
        var t = templateService.publish(TenantContext.getTenantIdAsObject(), req.code(), req.channel(), req.subject(),
                req.body(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Template published", TemplateResponse.of(t)));
    }

    @GetMapping("/communication-templates")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> templates() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                templateService.listLatest(TenantContext.getTenantIdAsObject()).stream().map(TemplateResponse::of).toList()));
    }

    @GetMapping("/communication-templates/{code}/versions")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> templateVersions(@PathVariable String code) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                templateService.versions(TenantContext.getTenantIdAsObject(), code).stream().map(TemplateResponse::of).toList()));
    }

    // ── opt-outs ─────────────────────────────────────────────────────────────

    @GetMapping("/communication-opt-outs")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<OptOutResponse>>> optOuts() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                optOutService.listActive(TenantContext.getTenantIdAsObject()).stream().map(OptOutResponse::of).toList()));
    }

    @PostMapping("/communication-opt-outs")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record a do-not-contact request (idempotent per contact + channel)")
    public ResponseEntity<ApiResponse<OptOutResponse>> recordOptOut(@Valid @RequestBody RecordOptOutRequest req) {
        featureGuard.requireModule("debtcollection");
        TenantId t = TenantContext.getTenantIdAsObject();
        var o = optOutService.record(t, req.contact(), req.channel(), req.reason(), "STAFF",
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Opt-out recorded", OptOutResponse.of(o)));
    }

    @PostMapping("/communication-opt-outs/{id}/revoke")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Lift an opt-out (supervisor) — reason mandatory, history retained")
    public ResponseEntity<ApiResponse<OptOutResponse>> revokeOptOut(@PathVariable UUID id,
                                                                    @Valid @RequestBody RevokeOptOutRequest req) {
        featureGuard.requireModule("debtcollection");
        var o = optOutService.revoke(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Opt-out lifted", OptOutResponse.of(o)));
    }
}
