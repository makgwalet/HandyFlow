package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.projects.application.internal.clientportal.ClientPortalService;
import za.co.handyflow.platform.projects.dto.ClientPortalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;
import java.util.UUID;

/**
 * What a logged-in client contact sees and does (portal JWT, PORTAL_USER authority). Nothing here reads TenantContext:
 * the tenant and the projects come from the caller's active grants, re-checked on every call.
 */
@RestController
@RequestMapping("/api/v1/project-client/portal/me")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Projects Client Portal", description = "A client contact's own projects, change orders, requests and files")
public class ClientPortalMeController {

    private final ClientPortalService portal;

    @GetMapping
    @Operation(summary = "Who is logged in and which companies they act for")
    public ResponseEntity<ApiResponse<Me>> me() { return ok(portal.me(user())); }

    @GetMapping("/projects")
    @Operation(summary = "The projects this login can see, with progress and what is waiting on the client")
    public ResponseEntity<ApiResponse<List<MyProject>>> projects() { return ok(portal.myProjects(user())); }

    @GetMapping("/projects/{projectId}")
    @Operation(summary = "One project: milestones, open snags, red risks, shared documents, approved change orders, requests and files")
    public ResponseEntity<ApiResponse<ProjectView>> project(@PathVariable UUID projectId) { return ok(portal.project(user(), projectId)); }

    @GetMapping("/projects/{projectId}/documents/{documentId}/download")
    @Operation(summary = "Download an approved document shared with the client")
    public ResponseEntity<byte[]> download(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        return DocumentDownloads.response(portal.download(user(), projectId, documentId));
    }

    @PostMapping("/projects/{projectId}/change-orders/{changeOrderId}/approve")
    @Operation(summary = "Approve a change order (its value and time effect) on behalf of the client")
    public ResponseEntity<ApiResponse<ClientChangeOrder>> approve(@PathVariable UUID projectId, @PathVariable UUID changeOrderId) {
        return ok(portal.approveChangeOrder(user(), projectId, changeOrderId));
    }

    @PostMapping("/projects/{projectId}/change-orders/{changeOrderId}/dispute")
    @Operation(summary = "Dispute a change order, saying why")
    public ResponseEntity<ApiResponse<ClientChangeOrder>> dispute(@PathVariable UUID projectId, @PathVariable UUID changeOrderId,
                                                                  @Valid @RequestBody DisputeRequest req) {
        return ok(portal.disputeChangeOrder(user(), projectId, changeOrderId, req.reason()));
    }

    @GetMapping("/requests")
    @Operation(summary = "New-project requests this login's companies sent")
    public ResponseEntity<ApiResponse<List<RequestView>>> newProjectRequests() { return ok(portal.newProjectRequests(user())); }

    @PostMapping("/requests")
    @Operation(summary = "Send a question, a change request (on a project) or a new-project request (with grantId when acting for several companies)")
    public ResponseEntity<ApiResponse<RequestView>> raise(@Valid @RequestBody RaiseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Request sent", portal.raise(user(), req)));
    }

    @PostMapping(value = "/projects/{projectId}/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Send a file to the project team (it waits for them to accept it into the register)")
    public ResponseEntity<ApiResponse<UploadView>> upload(@PathVariable UUID projectId, @RequestPart("file") MultipartFile file,
                                                          @RequestParam(value = "note", required = false) String note) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("File sent", portal.upload(user(), projectId, file, note)));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    /** PortalJwtFilter puts the portal user's id in the principal. */
    private static UUID user() {
        try {
            return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception e) {
            throw new HandyFlowException("Invalid portal session", HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_SESSION");
        }
    }
}
