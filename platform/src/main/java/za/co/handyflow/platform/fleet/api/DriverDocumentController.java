package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.DriverDocumentService;
import za.co.handyflow.platform.fleet.domain.model.DriverDocumentType;
import za.co.handyflow.platform.fleet.dto.DriverDocumentResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A driver's file: photo, licence and PrDP copies, medical and training certificates. Same permission split as the vehicle vault. */
@RestController
@RequestMapping("/api/v1/fleet/drivers/{driverId}/documents")
@RequiredArgsConstructor
@Tag(name = "Fleet - Driver Documents", description = "Driver photo, licence, PrDP, medical and training documents")
public class DriverDocumentController {

    private final DriverDocumentService documentService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "List a driver's documents (current only by default; includeSuperseded=true adds version history)")
    public ResponseEntity<ApiResponse<List<DriverDocumentResponse>>> list(
            @PathVariable UUID driverId,
            @RequestParam(required = false) DriverDocumentType type,
            @RequestParam(defaultValue = "false") boolean includeSuperseded) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                documentService.list(TenantContext.getTenantIdAsObject(), driverId, type, includeSuperseded)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Upload a driver document. Singleton types supersede the previous version; a training certificate needs a title "
            + "(the course) and a medical certificate needs an expiry date")
    public ResponseEntity<ApiResponse<DriverDocumentResponse>> upload(
            @PathVariable UUID driverId,
            @RequestPart("file") MultipartFile file,
            @RequestParam DriverDocumentType type,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String documentNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(required = false) String issuer,
            @RequestParam(required = false) String notes) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document uploaded",
                documentService.upload(TenantContext.getTenantIdAsObject(), driverId, UserContext.getCurrentUserId(),
                        file, type, title, documentNumber, issueDate, expiryDate, issuer, notes)));
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Download a driver document's file")
    public ResponseEntity<Resource> download(@PathVariable UUID driverId, @PathVariable UUID documentId) {
        featureGuard.requireModule("fleet");
        DriverDocumentService.DocumentDownload dl =
                documentService.download(TenantContext.getTenantIdAsObject(), driverId, documentId);
        // Always an attachment + nosniff: never let the browser render uploaded content inline in our origin.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(dl.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .contentLength(dl.sizeBytes())
                .body(new InputStreamResource(dl.stream()));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Delete a driver document (soft delete: the stored file is retained as evidence)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID driverId, @PathVariable UUID documentId) {
        featureGuard.requireModule("fleet");
        documentService.delete(TenantContext.getTenantIdAsObject(), driverId, documentId, UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }
}
