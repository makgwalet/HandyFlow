package za.co.handyflow.platform.accountant.api;

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
import za.co.handyflow.platform.accountant.application.internal.AccWorkpaperService;
import za.co.handyflow.platform.accountant.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Closes the accountant module audit's "larger workpaper system" gap.
 * Deliberately separate from AccountantController, matching
 * AccWorkpaperService's own reasoning for staying out of the
 * already-large AccountantService.
 */
@RestController
@RequestMapping("/api/v1/accountant")
@RequiredArgsConstructor
@Tag(name = "Accountant Workpapers", description = "Audit-workpaper folders, files, review workflow, and audit log")
public class AccWorkpaperController {

    private final AccWorkpaperService workpaperService;

    @PostMapping("/clients/{id}/workpaper-folders")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Create a workpaper folder for a client")
    public ResponseEntity<ApiResponse<WorkpaperFolderResponse>> createFolder(
            @PathVariable UUID id, @Valid @RequestBody CreateFolderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Folder created",
                workpaperService.createFolder(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/clients/{id}/workpaper-folders")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List workpaper folders for a client")
    public ResponseEntity<ApiResponse<List<WorkpaperFolderResponse>>> getFolders(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Workpaper folders",
                workpaperService.getFolders(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/workpaper-files")
    @PreAuthorize("hasAnyAuthority('USER_CREATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Upload a workpaper file (max 10MB) — re-uploading the same file name in the same folder creates a new version")
    public ResponseEntity<ApiResponse<WorkpaperFileResponse>> uploadFile(
            @PathVariable UUID id, @Valid @RequestBody UploadWorkpaperFileRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("File uploaded",
                workpaperService.uploadFile(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clients/{id}/workpaper-folders/{folderId}/files")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "List files in a workpaper folder")
    public ResponseEntity<ApiResponse<List<WorkpaperFileResponse>>> getFiles(
            @PathVariable UUID id, @PathVariable UUID folderId) {
        return ResponseEntity.ok(ApiResponse.success("Workpaper files",
                workpaperService.getFiles(TenantContext.getTenantIdAsObject(), id, folderId)));
    }

    @GetMapping(value = "/clients/{id}/workpaper-files/{fileId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Download a workpaper file")
    public ResponseEntity<byte[]> downloadFile(@PathVariable UUID id, @PathVariable UUID fileId) {
        var file = workpaperService.downloadFile(TenantContext.getTenantIdAsObject(), id, fileId,
                TenantContext.getCurrentUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .body(file.content());
    }

    @PostMapping("/clients/{id}/workpaper-files/{fileId}/status")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Move a workpaper file through the review workflow — DRAFT, PREPARED, REVIEWED, or SIGNED_OFF")
    public ResponseEntity<ApiResponse<WorkpaperFileResponse>> updateFileStatus(
            @PathVariable UUID id, @PathVariable UUID fileId, @Valid @RequestBody UpdateWorkpaperStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                workpaperService.updateFileStatus(TenantContext.getTenantIdAsObject(), id, fileId, req,
                        TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/clients/{id}/workpaper-files/{fileId}")
    @PreAuthorize("hasAnyAuthority('USER_DELETE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Soft-delete a workpaper file")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable UUID id, @PathVariable UUID fileId) {
        workpaperService.deleteFile(TenantContext.getTenantIdAsObject(), id, fileId, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("File deleted", null));
    }

    @PostMapping("/clients/{id}/workpaper-files/{fileId}/restore")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','ACCOUNTANT_WRITE')")
    @Operation(summary = "Restore a soft-deleted workpaper file")
    public ResponseEntity<ApiResponse<WorkpaperFileResponse>> restoreFile(
            @PathVariable UUID id, @PathVariable UUID fileId) {
        return ResponseEntity.ok(ApiResponse.success("File restored",
                workpaperService.restoreFile(TenantContext.getTenantIdAsObject(), id, fileId,
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clients/{id}/workpaper-files/{fileId}/audit")
    @PreAuthorize("hasAnyAuthority('USER_READ','ACCOUNTANT_READ')")
    @Operation(summary = "Audit log for a workpaper file — who uploaded, viewed, downloaded, or changed its status, and when")
    public ResponseEntity<ApiResponse<List<WorkpaperAuditResponse>>> getFileAuditLog(
            @PathVariable UUID id, @PathVariable UUID fileId) {
        return ResponseEntity.ok(ApiResponse.success("Audit log",
                workpaperService.getFileAuditLog(TenantContext.getTenantIdAsObject(), id, fileId)));
    }
}