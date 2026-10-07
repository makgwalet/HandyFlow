// security/api/GuardScreeningEvidenceController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.application.internal.GuardScreeningEvidenceService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Evidence files on a screening record. Reading needs SECURITY_READ; adding and removing SECURITY_MANAGE. */
@RestController
@RequestMapping("/api/v1/security/guards/{guardId}/screening/{screeningId}/evidence")
@RequiredArgsConstructor
@Tag(name = "Security - Screening evidence")
public class GuardScreeningEvidenceController {

    private final GuardScreeningEvidenceService evidenceService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List the evidence files attached to a screening")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> list(@PathVariable UUID guardId, @PathVariable UUID screeningId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                evidenceService.list(TenantContext.getTenantIdAsObject(), guardId, screeningId)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Attach an evidence file (certificate, verification response, report)")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attach(
            @PathVariable UUID guardId, @PathVariable UUID screeningId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String label) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Evidence attached", evidenceService.attach(
                TenantContext.getTenantIdAsObject(), guardId, screeningId, file, label,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{evidenceId}/download")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Download an evidence file")
    public ResponseEntity<byte[]> download(@PathVariable UUID guardId, @PathVariable UUID screeningId, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("security");
        var file = evidenceService.download(TenantContext.getTenantIdAsObject(), guardId, screeningId, evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @DeleteMapping("/{evidenceId}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Remove an evidence file (the stored file is preserved by the evidence module)")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID guardId, @PathVariable UUID screeningId, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("security");
        evidenceService.remove(TenantContext.getTenantIdAsObject(), guardId, screeningId, evidenceId);
        return ResponseEntity.ok(ApiResponse.success("Evidence removed", null));
    }
}
