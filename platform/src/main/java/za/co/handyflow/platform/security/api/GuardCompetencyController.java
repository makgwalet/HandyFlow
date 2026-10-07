// security/api/GuardCompetencyController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.application.internal.GuardCompetencyService;
import za.co.handyflow.platform.security.dto.GuardCompetencyResponse;
import za.co.handyflow.platform.security.dto.SaveGuardCompetencyRequest;
import za.co.handyflow.platform.security.dto.VerifyGuardCompetencyRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Competencies on a guard's file. Reading needs SECURITY_READ; every change SECURITY_MANAGE. */
@RestController
@RequestMapping("/api/v1/security/guards/{guardId}/competencies")
@RequiredArgsConstructor
@Tag(name = "Security - Guard competencies")
public class GuardCompetencyController {

    private final GuardCompetencyService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List a guard's competencies with their state and evidence")
    public ResponseEntity<ApiResponse<List<GuardCompetencyResponse>>> list(@PathVariable UUID guardId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), guardId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Add a competency")
    public ResponseEntity<ApiResponse<GuardCompetencyResponse>> create(@PathVariable UUID guardId, @Valid @RequestBody SaveGuardCompetencyRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Competency added",
                service.create(TenantContext.getTenantIdAsObject(), guardId, req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Edit a competency (removes the earlier verification)")
    public ResponseEntity<ApiResponse<GuardCompetencyResponse>> update(@PathVariable UUID guardId, @PathVariable UUID id, @Valid @RequestBody SaveGuardCompetencyRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Competency updated",
                service.update(TenantContext.getTenantIdAsObject(), guardId, id, req)));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Verify a competency (needs a certificate attached)")
    public ResponseEntity<ApiResponse<GuardCompetencyResponse>> verify(@PathVariable UUID guardId, @PathVariable UUID id, @RequestBody(required = false) VerifyGuardCompetencyRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Competency verified", service.verify(
                TenantContext.getTenantIdAsObject(), guardId, id, req == null ? null : req.note(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Remove a competency (soft delete)")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID guardId, @PathVariable UUID id) {
        featureGuard.requireModule("security");
        service.remove(TenantContext.getTenantIdAsObject(), guardId, id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Competency removed", null));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List the certificate files on a competency")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> files(@PathVariable UUID guardId, @PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.files(TenantContext.getTenantIdAsObject(), guardId, id)));
    }

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Attach a certificate file")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attach(@PathVariable UUID guardId, @PathVariable UUID id,
                                                                @RequestParam("file") MultipartFile file, @RequestParam(required = false) String label) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Evidence attached", service.attach(
                TenantContext.getTenantIdAsObject(), guardId, id, file, label,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence/{evidenceId}/download")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Download a certificate file")
    public ResponseEntity<byte[]> download(@PathVariable UUID guardId, @PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("security");
        var file = service.download(TenantContext.getTenantIdAsObject(), guardId, id, evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @DeleteMapping("/{id}/evidence/{evidenceId}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Remove a certificate file (removes the verification if it was the last one)")
    public ResponseEntity<ApiResponse<Void>> removeFile(@PathVariable UUID guardId, @PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("security");
        service.removeFile(TenantContext.getTenantIdAsObject(), guardId, id, evidenceId);
        return ResponseEntity.ok(ApiResponse.success("Evidence removed", null));
    }
}
