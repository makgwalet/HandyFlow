package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpDocumentService;
import za.co.handyflow.platform.legalpractice.dto.DocumentDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Matter documents with version history, check-out, legal hold and an event log. The files never appear in the general evidence screens. */
@RestController
@RequestMapping("/api/v1/legal-practice/documents")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Documents", description = "Matter documents, versions, check-out, legal hold")
public class LpDocumentController {

    private static final String READ = "hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_DOCUMENT_WRITE','LEGALPRACTICE_DOCUMENT_HOLD','LEGALPRACTICE_DOCUMENT_OVERRIDE','LEGALPRACTICE_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('LEGALPRACTICE_DOCUMENT_WRITE','LEGALPRACTICE_ADMIN')";
    private static final String HOLD = "hasAnyAuthority('LEGALPRACTICE_DOCUMENT_HOLD','LEGALPRACTICE_ADMIN')";
    private static final String OVERRIDE = "hasAnyAuthority('LEGALPRACTICE_DOCUMENT_OVERRIDE','LEGALPRACTICE_ADMIN')";

    private final LpDocumentService service;
    private final FeatureGuard featureGuard;

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) { return ResponseEntity.ok(ApiResponse.success(body)); }

    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "Documents across the firm (leaves out matters the caller is walled from)")
    public ResponseEntity<ApiResponse<Page<DocumentSummary>>> firm(@RequestParam(required = false) String search, @RequestParam(required = false) String category,
                                                                   @RequestParam(defaultValue = "false") boolean includeArchived, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ok(service.firmList(TenantContext.getTenantIdAsObject(), search, category, includeArchived, pageable));
    }

    @GetMapping("/matters/{matterId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<DocumentSummary>>> forMatter(@PathVariable UUID matterId) {
        featureGuard.requireModule("legalpractice");
        return ok(service.forMatter(TenantContext.getTenantIdAsObject(), matterId));
    }

    @PostMapping(value = "/matters/{matterId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    @Operation(summary = "Add a document to a matter (version 1)")
    public ResponseEntity<ApiResponse<DocumentDetail>> upload(@PathVariable UUID matterId, @RequestParam("file") MultipartFile file, @RequestParam String title,
                                                              @RequestParam String category, @RequestParam(required = false) String note) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document added",
                service.upload(TenantContext.getTenantIdAsObject(), matterId, title, category, file, note, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<DocumentDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.get(TenantContext.getTenantIdAsObject(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<DocumentDetail>> edit(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.edit(TenantContext.getTenantIdAsObject(), id, req.title(), req.category(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    @Operation(summary = "Add a new version. If you have the document checked out, this checks it in")
    public ResponseEntity<ApiResponse<DocumentDetail>> addVersion(@PathVariable UUID id, @RequestParam("file") MultipartFile file, @RequestParam(required = false) String note) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Version added",
                service.addVersion(TenantContext.getTenantIdAsObject(), id, file, note, false, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping(value = "/{id}/checkin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    @Operation(summary = "Check in the document you have checked out, with its new version")
    public ResponseEntity<ApiResponse<DocumentDetail>> checkIn(@PathVariable UUID id, @RequestParam("file") MultipartFile file, @RequestParam(required = false) String note) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Checked in",
                service.addVersion(TenantContext.getTenantIdAsObject(), id, file, note, true, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize(READ)
    @Operation(summary = "Download a version (the current one unless version is given). The download is logged")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @RequestParam(required = false) Integer version) {
        featureGuard.requireModule("legalpractice");
        LpDocumentService.DocumentFile f = service.download(TenantContext.getTenantIdAsObject(), id, version, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(f.contentType()))
                .body(f.content());
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize(READ)
    @Operation(summary = "Check a stored file against the hash taken when it was received")
    public ResponseEntity<ApiResponse<VerifyResponse>> verify(@PathVariable UUID id, @RequestParam(required = false) Integer version) {
        featureGuard.requireModule("legalpractice");
        return ok(service.verify(TenantContext.getTenantIdAsObject(), id, version, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @PostMapping("/{id}/checkout")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<DocumentDetail>> checkOut(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.checkOut(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @PostMapping("/{id}/cancel-checkout")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<DocumentDetail>> cancelCheckout(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.cancelCheckout(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @PostMapping("/{id}/override-checkout")
    @PreAuthorize(OVERRIDE)
    @Operation(summary = "Release a check-out held by someone else, with a reason")
    public ResponseEntity<ApiResponse<DocumentDetail>> overrideCheckout(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.overrideCheckout(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize(HOLD)
    @Operation(summary = "Place a document under legal hold: it cannot be archived while held")
    public ResponseEntity<ApiResponse<DocumentDetail>> hold(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.setHold(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @PostMapping("/{id}/release-hold")
    @PreAuthorize(HOLD)
    public ResponseEntity<ApiResponse<DocumentDetail>> releaseHold(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.releaseHold(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<DocumentDetail>> archive(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalpractice");
        return ok(service.archive(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<DocumentDetail>> restore(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ok(service.restore(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }
}
