// security/api/GuardPostOrderController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.DeviceSessionService;
import za.co.handyflow.platform.security.application.internal.PostOrderService;
import za.co.handyflow.platform.security.dto.AcknowledgePostOrderRequest;
import za.co.handyflow.platform.security.dto.MyPostResponse;
import za.co.handyflow.platform.security.dto.PostOrderAcknowledgementResponse;
import za.co.handyflow.platform.security.dto.PostOrderResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * "My Post" — a guard's own view of what they're supposed to do at
 * their current location, per the product owner's own explicit design:
 * "A security guard shouldn't open an app and see only: Start shift ->
 * Scan checkpoint -> Finish shift. The app should actually help them
 * perform their job." Site is resolved from the guard's own open
 * session, same posture already established for GAP-07's gate-access
 * reads and GAP-08's shift-swap history — never a free siteId
 * parameter the caller supplies.
 */
@Tag(name = "Guard - My Post", description = "Guard-facing post orders and acknowledgment (mobile)")
@RestController
@RequestMapping("/api/v1/guard/my-post")
@RequiredArgsConstructor
public class GuardPostOrderController {

    private final PostOrderService postOrderService;
    private final DeviceSessionService deviceSessionService;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "My Post — this guard's site-level order plus their site's posts",
            description = "Site resolved from the guard's own open session. Fails with 409/NO_OPEN_SESSION if the guard isn't clocked in.")
    public ResponseEntity<ApiResponse<MyPostResponse>> getMyPost() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID guardId = TenantContext.getCurrentUserId();
        UUID siteId = deviceSessionService.resolveCurrentSiteForGuard(guardId);
        return ResponseEntity.ok(ApiResponse.success(postOrderService.getMyPost(tenantId, siteId, guardId)));
    }

    @GetMapping("/posts/{postId}")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "A specific post's own current order",
            description = "The guard picks their own post from MyPostResponse's own posts list — no formal post-assignment concept exists yet.")
    public ResponseEntity<ApiResponse<PostOrderResponse>> getPostOrder(@PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.success(
                postOrderService.getMyPostOrderForPost(TenantContext.getTenantIdAsObject(), postId)));
    }

    @PostMapping("/orders/{postOrderId}/acknowledge")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Acknowledge a post order version — the evidence record",
            description = "\"That creates evidence.\" Records exactly which version was read, by whom, on which device, and whether it happened offline.")
    public ResponseEntity<ApiResponse<PostOrderAcknowledgementResponse>> acknowledge(
            @PathVariable UUID postOrderId, @Valid @RequestBody AcknowledgePostOrderRequest req) {
        UUID guardId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                postOrderService.acknowledge(TenantContext.getTenantIdAsObject(), postOrderId, guardId, req)));
    }
}
