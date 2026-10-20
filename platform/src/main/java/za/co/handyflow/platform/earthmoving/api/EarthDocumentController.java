package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade.DocumentInfo;
import za.co.handyflow.platform.documents.application.DocumentsFacade.FileUpload;
import za.co.handyflow.platform.documents.application.DocumentsFacade.VersionInfo;
import za.co.handyflow.platform.earthmoving.application.internal.EarthDocumentService;
import za.co.handyflow.platform.earthmoving.dto.ArchiveDocumentRequest;
import za.co.handyflow.platform.earthmoving.dto.UpdateDocumentRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Photos, manuals, insurance, warranties and operator certificates. The permission each call needs depends on the document's category (the service
 * decides), so the endpoints only require a signed-in Earthmoving user.
 */
@RestController
@RequestMapping("/api/v1/earthmoving/documents")
@RequiredArgsConstructor
@Tag(name = "Earthmoving documents")
public class EarthDocumentController {

    private final EarthDocumentService documents;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Documents of a machine (ownerKind ASSET) or an operator (ownerKind OPERATOR)")
    public ResponseEntity<ApiResponse<List<DocumentInfo>>> list(@RequestParam String ownerKind, @RequestParam UUID ownerId,
                                                                @RequestParam(defaultValue = "false") boolean includeArchived) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Documents", documents.list(TenantContext.getTenantIdAsObject(), ownerKind, ownerId, includeArchived)));
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Documents that have expired or expire within the days given")
    public ResponseEntity<ApiResponse<List<DocumentInfo>>> expiring(@RequestParam(defaultValue = "60") int withinDays) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Expiring documents", documents.expiring(TenantContext.getTenantIdAsObject(), withinDays)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE') or hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Add a document with its first file")
    public ResponseEntity<ApiResponse<DocumentInfo>> upload(@RequestParam String ownerKind, @RequestParam UUID ownerId, @RequestParam String category,
                                                            @RequestParam String title, @RequestParam(required = false) String description,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
                                                            @RequestParam("file") MultipartFile file) throws IOException {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Document added", documents.upload(TenantContext.getTenantIdAsObject(), ownerKind, ownerId, category, title,
                description, expiresOn, upload(file), UserContext.getCurrentUserId())));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE') or hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Add a new version of a document, optionally with its renewed expiry date")
    public ResponseEntity<ApiResponse<DocumentInfo>> addVersion(@PathVariable UUID id, @RequestParam(required = false) String note,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
                                                                @RequestParam("file") MultipartFile file) throws IOException {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Version added",
                documents.addVersion(TenantContext.getTenantIdAsObject(), id, upload(file), note, expiresOn, UserContext.getCurrentUserId())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE') or hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Change a document's title, description or expiry date")
    public ResponseEntity<ApiResponse<DocumentInfo>> update(@PathVariable UUID id, @RequestBody UpdateDocumentRequest req) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Document updated", documents.updateDetails(TenantContext.getTenantIdAsObject(), id, req.title(),
                req.description(), req.expiresOn(), UserContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Archive a document (its files are kept)")
    public ResponseEntity<ApiResponse<DocumentInfo>> archive(@PathVariable UUID id, @RequestBody ArchiveDocumentRequest req) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Document archived",
                documents.archive(TenantContext.getTenantIdAsObject(), id, req.reason(), UserContext.getCurrentUserId())));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "A document's versions, newest first")
    public ResponseEntity<ApiResponse<List<VersionInfo>>> versions(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Versions", documents.versions(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Download the latest version, or the numbered one")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @RequestParam(required = false) Integer version) {
        featureGuard.requireModule("earthmoving");
        DocumentsFacade.Download file = documents.download(TenantContext.getTenantIdAsObject(), id, version, UserContext.getCurrentUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName().replace("\"", "") + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    private static FileUpload upload(MultipartFile f) throws IOException {
        return new FileUpload(f.getOriginalFilename(), f.getContentType(), f.getBytes());
    }
}
