package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import za.co.handyflow.platform.admin.application.internal.AdminNotificationService;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Notifications", description = "Real-time SSE notifications for admin portal")
public class AdminNotificationController {

    private final AdminNotificationService notificationService;

    /**
     * SSE endpoint — browser connects here and receives pushed events.
     *
     * NOTE: This endpoint must NOT be authenticated via the JwtAuthFilter
     * in the standard way, because SSE connections are long-lived HTTP streams
     * and some proxies strip Authorization headers on keep-alive connections.
     * Instead, we pass the admin token as a query parameter (less ideal but
     * necessary for EventSource which doesn't support custom headers in browsers).
     *
     * The @PreAuthorize on the class still protects all other endpoints.
     * Override for SSE only if needed — for now the filter handles it.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE stream — connect to receive real-time admin notifications")
    public SseEmitter stream() {
        UUID adminId = getAdminId();
        return notificationService.subscribe(adminId);
    }

    @GetMapping
    @Operation(summary = "List recent notifications — last 50, newest first")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getNotifications(
            @RequestParam(defaultValue = "50")   int     limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getNotifications(getAdminId(), limit, unreadOnly)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count unread notifications in the last 7 days")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getUnreadCount() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", notificationService.getUnreadCount(getAdminId()))));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read by this admin")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID id) {
        notificationService.markRead(id, getAdminId());
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read by this admin")
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        notificationService.markAllRead(getAdminId());
        return ResponseEntity.ok(ApiResponse.success("All marked as read", null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID getAdminId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return UUID.fromString(auth.getPrincipal().toString());
    }
}
