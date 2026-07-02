package za.co.handyflow.platform.projects.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.RfiService;
import za.co.handyflow.platform.projects.dto.CreateRfiRequest;
import za.co.handyflow.platform.projects.dto.RespondRfiRequest;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Validated
public class RfiController {

    private final RfiService rfiService;

    @GetMapping("/{projectId}/rfis")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<?> getRfis(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.getRfis(projectId)));
    }

    @GetMapping("/rfis/{rfiId}")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<?> getRfi(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success(rfiService.getRfi(rfiId)));
    }

    @PostMapping("/{projectId}/rfis")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> createRfi(@PathVariable UUID projectId,
                                       @Valid @RequestBody CreateRfiRequest req) {
        return ResponseEntity.status(201)
                .body(ApiResponse.success("RFI created", rfiService.createRfi(projectId, req)));
    }

    /** DRAFT → SUBMITTED */
    @PostMapping("/rfis/{rfiId}/submit")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> submit(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success("RFI submitted", rfiService.submit(rfiId)));
    }

    /** SUBMITTED → RESPONDED */
    @PostMapping("/rfis/{rfiId}/respond")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> respond(@PathVariable UUID rfiId,
                                     @Valid @RequestBody RespondRfiRequest req) {
        return ResponseEntity.ok(ApiResponse.success("RFI responded", rfiService.respond(rfiId, req)));
    }

    /** RESPONDED → CLOSED */
    @PostMapping("/rfis/{rfiId}/close")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> close(@PathVariable UUID rfiId) {
        return ResponseEntity.ok(ApiResponse.success("RFI closed", rfiService.close(rfiId)));
    }

    /** Any open status → CANCELLED */
    @PostMapping("/rfis/{rfiId}/cancel")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<?> cancel(@PathVariable UUID rfiId,
                                    @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success("RFI cancelled", rfiService.cancel(rfiId, reason)));
    }
}
