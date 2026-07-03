package za.co.handyflow.platform.notifications.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.notifications.application.internal.NotificationQueryService;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.dto.NotificationPreferenceResponse;
import za.co.handyflow.platform.notifications.dto.NotificationResponse;
import za.co.handyflow.platform.notifications.dto.UpdateNotificationPreferenceRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification centre and delivery preferences")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List notifications for the current user, newest first")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationQueryService.getForUser(
                        TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(), unreadOnly, pageable)));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Unread notification count, for the bell badge")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount() {
        long count = notificationQueryService.getUnreadCount(
                TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", count)));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark a single notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationQueryService.markRead(
                        TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(), id)));
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark all of the current user's notifications as read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        int updated = notificationQueryService.markAllRead(
                TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated)));
    }

    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the current user's EMAIL/SMS notification preferences")
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> getPreferences() {
        return ResponseEntity.ok(ApiResponse.success(
                notificationQueryService.getPreferences(
                        TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId())));
    }

    @PutMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Enable/disable a delivery channel (EMAIL or SMS) for the current user")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreference(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        NotificationChannel channel = NotificationChannel.valueOf(request.channel());
        return ResponseEntity.ok(ApiResponse.success(
                notificationQueryService.updatePreference(
                        TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(),
                        channel, request.enabled())));
    }
}