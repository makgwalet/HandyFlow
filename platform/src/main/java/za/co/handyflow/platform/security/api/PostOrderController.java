// security/api/PostOrderController.java

package za.co.handyflow.platform.security.api;

import za.co.handyflow.platform.security.application.internal.SiteScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.PostOrderService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Supervisor-facing Post Orders management — Site + Post hierarchy,
 * versioned orders, publishing, contacts. Guard-facing reads and
 * acknowledgment live separately in GuardPostOrderController under
 * /api/v1/guard/**, matching this whole session's own established
 * split between the tenant-JWT surface and GuardJwtFilter's surface.
 */
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Security - Post Orders", description = "Site/Post hierarchy, versioned post orders, reusable contacts")
public class PostOrderController {

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    /** A site outside the user's scope answers exactly like a site that does not exist. */
    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** For a list whose site is optional: a user limited to some sites must name one of them, the whole company may leave it out. */
    private void choice(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkChoice(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** For a view that spans every site and cannot be narrowed yet: only a user with the whole company may use it. */
    private void wholeCompany() {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    /** A record that belongs to a site: one at a site outside the user's scope answers "not found". */
    private void child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind kind, UUID id) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkChild(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), kind, id);
    }

    /** For a site that is optional in the request: leaving it out means the whole company. */
    private void optionalSite(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkOptionalSite(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    private final PostOrderService postOrderService;

    // ── Contacts ─────────────────────────────────────────────────────────────

    @PostMapping("/contacts")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<SecurityContactResponse>> createContact(
            @Valid @RequestBody CreateContactRequest req) {
        optionalSite(req.siteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                postOrderService.createContact(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/contacts/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<SecurityContactResponse>> updateContact(
            @PathVariable UUID id, @Valid @RequestBody CreateContactRequest req) {
        child(SiteScopeService.Kind.CONTACT, id);
        return ResponseEntity.ok(ApiResponse.success(
                postOrderService.updateContact(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/contacts/{id}/deactivate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivateContact(@PathVariable UUID id) {
        child(SiteScopeService.Kind.CONTACT, id);
        postOrderService.deactivateContact(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/sites/{siteId}/contacts")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Contacts available at a site", description = "Tenant-wide contacts (e.g. Police) plus contacts scoped to this specific site.")
    public ResponseEntity<ApiResponse<List<SecurityContactResponse>>> getContactsForSite(@PathVariable UUID siteId) {
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success(
                postOrderService.getContactsForSite(TenantContext.getTenantIdAsObject(), siteId)));
    }

    // ── Posts ────────────────────────────────────────────────────────────────

    @PostMapping("/sites/{siteId}/posts")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @PathVariable UUID siteId, @Valid @RequestBody CreatePostRequest req) {
        site(siteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                postOrderService.createPost(TenantContext.getTenantIdAsObject(), siteId, req)));
    }

    @PutMapping("/posts/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable UUID id, @Valid @RequestBody CreatePostRequest req) {
        child(SiteScopeService.Kind.POST, id);
        return ResponseEntity.ok(ApiResponse.success(
                postOrderService.updatePost(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/posts/{id}/deactivate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivatePost(@PathVariable UUID id) {
        child(SiteScopeService.Kind.POST, id);
        postOrderService.deactivatePost(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/sites/{siteId}/posts")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getPostsForSite(@PathVariable UUID siteId) {
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success(
                postOrderService.getPostsForSite(TenantContext.getTenantIdAsObject(), siteId)));
    }

    // ── Post Orders ──────────────────────────────────────────────────────────

    @PostMapping("/sites/{siteId}/post-orders")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Create a new draft version",
            description = "postId null in the request body creates a site-level draft; set creates a post-level draft. Version number is assigned automatically.")
    public ResponseEntity<ApiResponse<PostOrderResponse>> createDraft(
            @PathVariable UUID siteId, @Valid @RequestBody CreatePostOrderDraftRequest req) {
        site(siteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                postOrderService.createDraft(TenantContext.getTenantIdAsObject(), siteId, req,
                        TenantContext.getCurrentUserId())));
    }

    @PutMapping("/post-orders/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Edit a draft in place", description = "Only a DRAFT version can be edited — publish a new draft to change an already-published version.")
    public ResponseEntity<ApiResponse<PostOrderResponse>> updateDraft(
            @PathVariable UUID id, @Valid @RequestBody CreatePostOrderDraftRequest req) {
        child(SiteScopeService.Kind.POST_ORDER, id);
        return ResponseEntity.ok(ApiResponse.success(
                postOrderService.updateDraft(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/post-orders/{id}/publish")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Publish a draft — becomes ACTIVE, supersedes the previous version")
    public ResponseEntity<ApiResponse<PostOrderResponse>> publish(@PathVariable UUID id) {
        child(SiteScopeService.Kind.POST_ORDER, id);
        return ResponseEntity.ok(ApiResponse.success(
                postOrderService.publish(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/post-orders/{id}/attachments")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> addAttachment(
            @PathVariable UUID id, @Valid @RequestBody AddPostOrderAttachmentRequest req) {
        child(SiteScopeService.Kind.POST_ORDER, id);
        postOrderService.addAttachment(TenantContext.getTenantIdAsObject(), id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @GetMapping("/sites/{siteId}/post-orders/history")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Version history for a site-level or post-level order",
            description = "Pass postId as a query param to get a post's own history; omit for the site-level history.")
    public ResponseEntity<ApiResponse<List<PostOrderResponse>>> getHistory(
            @PathVariable UUID siteId, @RequestParam(required = false) UUID postId) {
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success(
                postOrderService.getHistory(TenantContext.getTenantIdAsObject(), siteId, postId)));
    }

    @GetMapping("/post-orders/{id}/acknowledgements")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Which guards have acknowledged this version — the compliance/evidence view")
    public ResponseEntity<ApiResponse<List<PostOrderAcknowledgementResponse>>> getAcknowledgements(@PathVariable UUID id) {
        child(SiteScopeService.Kind.POST_ORDER, id);
        return ResponseEntity.ok(ApiResponse.success(
                postOrderService.getAcknowledgementsForOrder(TenantContext.getTenantIdAsObject(), id)));
    }
}
