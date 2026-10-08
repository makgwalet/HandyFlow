package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicRecallService;
import za.co.handyflow.platform.clinic.dto.RecallActionRequest;
import za.co.handyflow.platform.clinic.dto.RecallPage;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/recalls")
@RequiredArgsConstructor
@Tag(name = "Clinic Recalls", description = "Patients due for a follow-up, derived from consultation followUpDays, with a call/snooze/dismiss log")
public class ClinicRecallController {

    private final ClinicRecallService recallService;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_RECALL_READ')")
    @Operation(summary = "Recall worklist: search, filter (ALL, OVERDUE, TODAY, NOT_CONTACTED, SNOOZED, DISMISSED), doctor, paging")
    public ResponseEntity<ApiResponse<RecallPage>> worklist(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) UUID practitionerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                recallService.worklist(TenantContext.getTenantIdAsObject(), q, filter, practitionerId, page, size)));
    }

    @PostMapping("/{consultationId}/actions")
    @PreAuthorize("hasAuthority('CLINIC_RECALL_MANAGE')")
    @Operation(summary = "Log a call, snooze, dismiss (with reason) or reopen a recall")
    public ResponseEntity<ApiResponse<Void>> act(@PathVariable UUID consultationId, @RequestBody RecallActionRequest body) {
        recallService.act(TenantContext.getTenantIdAsObject(), consultationId, body, UserContext.getCurrentUserId(), Instant.now());
        return ResponseEntity.ok(ApiResponse.success("Recall updated", null));
    }
}
