package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.TransmittalService;
import za.co.handyflow.platform.projects.dto.AcknowledgeTransmittalRequest;
import za.co.handyflow.platform.projects.dto.CreateTransmittalRequest;
import za.co.handyflow.platform.projects.dto.TransmittalResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Transmittal register — a record of which revisions were sent to whom. Project-first URLs, so project access rules apply automatically. */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/transmittals")
@RequiredArgsConstructor
@Tag(name = "Transmittals", description = "Record of documents issued to recipients")
public class TransmittalController {

    private final TransmittalService transmittalService;

    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Transmittal register, newest first")
    public ResponseEntity<ApiResponse<List<TransmittalResponse>>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                transmittalService.list(TenantContext.getTenantIdAsObject(), projectId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "One transmittal with the exact revisions it covered")
    public ResponseEntity<ApiResponse<TransmittalResponse>> get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                transmittalService.get(TenantContext.getTenantIdAsObject(), projectId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Record a transmittal. Every document must be live (CURRENT / FOR_REVIEW / APPROVED) — otherwise the whole request is refused. Nothing is emailed.")
    public ResponseEntity<ApiResponse<TransmittalResponse>> create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateTransmittalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Transmittal recorded",
                transmittalService.create(TenantContext.getTenantIdAsObject(), projectId, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Record that the recipient acknowledged receipt (once only)")
    public ResponseEntity<ApiResponse<TransmittalResponse>> acknowledge(
            @PathVariable UUID projectId, @PathVariable UUID id,
            @Valid @RequestBody AcknowledgeTransmittalRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Acknowledgement recorded",
                transmittalService.acknowledge(TenantContext.getTenantIdAsObject(), projectId, id, req.acknowledgedBy())));
    }
}