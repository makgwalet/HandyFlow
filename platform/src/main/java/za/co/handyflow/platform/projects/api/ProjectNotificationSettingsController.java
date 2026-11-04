package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.notify.ProjectNotificationSettings;
import za.co.handyflow.platform.projects.application.internal.notify.ProjectNotificationSettings.Setting;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;

/** The signed-in person's own email settings for Projects notifications (the bell always gets them). */
@RestController
@RequestMapping("/api/v1/projects/notification-settings")
@RequiredArgsConstructor
@Tag(name = "Project notifications", description = "Which Projects events email you")
public class ProjectNotificationSettingsController {

    private final ProjectNotificationSettings settings;

    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Your email setting for each Projects event")
    public ResponseEntity<ApiResponse<List<Setting>>> mine() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                settings.forUser(TenantContext.getTenantIdAsObject().getValue(), TenantContext.getCurrentUserId())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Switch email on or off per event: { \"TASK_ASSIGNED\": true, \"ACTION_ASSIGNED\": false }")
    public ResponseEntity<ApiResponse<List<Setting>>> update(@RequestBody Map<String, Boolean> email) {
        return ResponseEntity.ok(ApiResponse.success("Saved",
                settings.update(TenantContext.getTenantIdAsObject().getValue(), TenantContext.getCurrentUserId(), email)));
    }
}
