package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditWorkpaperDownloadService;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal-audit/workpaper-files/{fileId}/content")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Workpaper download", description = "The file behind a workpaper")
public class AuditWorkpaperDownloadController {
    private final AuditWorkpaperDownloadService service;
    private final FeatureGuard featureGuard;

    @GetMapping(produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Download a workpaper file (recorded in the audit trail)")
    public ResponseEntity<byte[]> get(@PathVariable UUID fileId) {
        featureGuard.requireModule("internal-audit");
        var d = service.download(TenantContext.getTenantIdAsObject(), fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.fileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(d.mimeType()))
                .body(d.content());
    }
}
