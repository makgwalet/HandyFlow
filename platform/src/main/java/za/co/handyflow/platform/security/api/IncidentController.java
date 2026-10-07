// security/api/IncidentController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.application.internal.IncidentCaseService;
import za.co.handyflow.platform.security.application.internal.IncidentPdfService;
import za.co.handyflow.platform.security.application.internal.IncidentService;
import za.co.handyflow.platform.security.dto.IncidentCaseDtos.*;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/incidents")
@RequiredArgsConstructor
@Tag(name = "Security - Incidents", description = "Incident reporting and lifecycle management")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentCaseService caseService;
    private final IncidentPdfService  pdfService;
    private final FeatureGuard    featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(
            summary = "List incidents with optional status/severity filters",
            description = "Paginated and sorted in SQL (fixes in-memory filtering bug). " +
                    "Supports ?sort=severity,desc or ?sort=createdAt,asc etc."
    )
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> getIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Success",
                incidentService.getIncidents(
                        TenantContext.getTenantIdAsObject(), status, severity, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Report a new incident",
            description = "siteId and guardId are validated as belonging to this tenant. " +
                    "incident.type is now set from the request (THEFT, FIRE, ASSAULT, etc.)."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(
            @Valid @RequestBody CreateIncidentRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(201).body(ApiResponse.success("Incident reported",
                incidentService.createIncident(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Acknowledge an incident — OPEN → ACKNOWLEDGED",
            description = "Records who acknowledged and when (fixes missing acknowledgedBy audit trail)."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> acknowledge(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        UUID actorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Acknowledged",
                incidentService.acknowledge(TenantContext.getTenantIdAsObject(), id, actorId, TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Resolve an incident — ACKNOWLEDGED → RESOLVED",
            description = "Records who resolved and when (fixes missing resolvedBy audit trail)."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> resolve(@PathVariable UUID id, @RequestBody(required = false) ResolveRequest body) {
        featureGuard.requireModule("security");
        UUID actorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Resolved",
                incidentService.resolve(TenantContext.getTenantIdAsObject(), id, actorId, TenantContext.getCurrentUserName(), body == null ? null : body.note())));
    }

    // ── Case view: timeline, assignment, escalation, evidence, report ─────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "One incident with its timeline, evidence, assignee and the actions available now")
    public ResponseEntity<ApiResponse<CaseDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(caseService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Say who is dealing with the incident")
    public ResponseEntity<ApiResponse<CaseDetail>> assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Assigned",
                caseService.assign(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Raise the severity (one level, or to a named level) with a reason")
    public ResponseEntity<ApiResponse<CaseDetail>> escalate(@PathVariable UUID id, @Valid @RequestBody EscalateRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Escalated",
                caseService.escalate(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Add a note to the timeline")
    public ResponseEntity<ApiResponse<CaseDetail>> note(@PathVariable UUID id, @Valid @RequestBody NoteRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Note added",
                caseService.note(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Reopen a resolved incident, with a reason")
    public ResponseEntity<ApiResponse<CaseDetail>> reopen(@PathVariable UUID id, @Valid @RequestBody ReopenRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Reopened",
                caseService.reopen(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Attach an evidence file (photo, video, document)")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attach(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                                                @RequestParam(value = "label", required = false) String label) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                caseService.attach(TenantContext.getTenantIdAsObject(), id, file, label, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence/{evidenceId}/download")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Download an evidence file")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("security");
        var d = caseService.download(TenantContext.getTenantIdAsObject(), id, evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.fileName().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(d.contentType() == null ? "application/octet-stream" : d.contentType()))
                .body(d.content());
    }

    @DeleteMapping("/{id}/evidence/{evidenceId}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Remove an evidence file")
    public ResponseEntity<ApiResponse<Void>> removeFile(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("security");
        caseService.removeFile(TenantContext.getTenantIdAsObject(), id, evidenceId, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Evidence removed", null));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Incident report as a PDF: details, description, assignee, evidence on file and timeline")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        CaseDetail c = caseService.get(tenantId, id);
        byte[] bytes = pdfService.incidentPdf(c.incident(), c.assigneeName(), c.events(),
                c.evidence().stream().map(e -> e.fileName()).toList(), tenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"incident-" + id.toString().substring(0, 8) + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}