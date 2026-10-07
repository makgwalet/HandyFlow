// security/api/GuardComplaintController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.application.internal.GuardComplaintService;
import za.co.handyflow.platform.security.dto.GuardComplaintDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Guard complaints. Reading needs SECURITY_READ; logging and every workflow step SECURITY_MANAGE. */
@RestController
@RequestMapping("/api/v1/security/complaints")
@RequiredArgsConstructor
@Tag(name = "Security - Guard complaints")
public class GuardComplaintController {

    private final GuardComplaintService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List complaints; status may be a status name or OPEN")
    public ResponseEntity<ApiResponse<Page<ComplaintSummary>>> list(
            @RequestParam(required = false) String status, @RequestParam(required = false) String severity,
            @RequestParam(required = false) String category, @RequestParam(required = false) UUID guardId,
            @PageableDefault(size = 25, sort = {"occurredOn", "createdAt"}, direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                service.list(TenantContext.getTenantIdAsObject(), status, severity, category, guardId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "One complaint with its timeline, evidence and the steps now available")
    public ResponseEntity<ApiResponse<ComplaintDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Log a complaint about a guard")
    public ResponseEntity<ApiResponse<ComplaintDetail>> log(@Valid @RequestBody SaveComplaintRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Complaint logged",
                service.log(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Edit the details (until a finding is made)")
    public ResponseEntity<ApiResponse<ComplaintDetail>> update(@PathVariable UUID id, @Valid @RequestBody SaveComplaintRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Complaint updated",
                service.update(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Start the investigation")
    public ResponseEntity<ApiResponse<ComplaintDetail>> start(@PathVariable UUID id, @RequestBody(required = false) StartInvestigationRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.start(TenantContext.getTenantIdAsObject(), id, req,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/finding")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Record the finding: substantiated, unsubstantiated or inconclusive")
    public ResponseEntity<ApiResponse<ComplaintDetail>> finding(@PathVariable UUID id, @Valid @RequestBody FindingRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.finding(TenantContext.getTenantIdAsObject(), id, req,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/action")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Record the action taken (recording only: it does not change the guard's status)")
    public ResponseEntity<ApiResponse<ComplaintDetail>> action(@PathVariable UUID id, @Valid @RequestBody ActionRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.action(TenantContext.getTenantIdAsObject(), id, req,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Close the complaint with a resolution")
    public ResponseEntity<ApiResponse<ComplaintDetail>> close(@PathVariable UUID id, @RequestBody CloseRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.close(TenantContext.getTenantIdAsObject(), id, req,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Withdraw a complaint that has not reached a finding")
    public ResponseEntity<ApiResponse<ComplaintDetail>> withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.withdraw(TenantContext.getTenantIdAsObject(), id, req,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Reopen a closed complaint for further investigation")
    public ResponseEntity<ApiResponse<ComplaintDetail>> reopen(@PathVariable UUID id, @Valid @RequestBody ReopenRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.reopen(TenantContext.getTenantIdAsObject(), id, req,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Attach an evidence file")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attach(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                                                @RequestParam(required = false) String label) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Evidence attached", service.attach(TenantContext.getTenantIdAsObject(), id, file, label,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence/{evidenceId}/download")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Download an evidence file")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("security");
        var file = service.download(TenantContext.getTenantIdAsObject(), id, evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @DeleteMapping("/{id}/evidence/{evidenceId}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Remove an evidence file from an open complaint")
    public ResponseEntity<ApiResponse<Void>> removeFile(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("security");
        service.removeFile(TenantContext.getTenantIdAsObject(), id, evidenceId,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Evidence removed", null));
    }
}
