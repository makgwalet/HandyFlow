package za.co.handyflow.platform.accountant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accountant.application.internal.ClientAssignmentService;
import za.co.handyflow.platform.accountant.application.internal.EngagementService;
import za.co.handyflow.platform.accountant.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Finance patch F15: who works on each client, and review and sign-off of a client's financial year. These URLs sit under
 * {@code /clients/{id}}, so the client-access filter already limits them to people who may see the client.
 */
@RestController
@RequestMapping("/api/v1/accountant/clients/{clientId}")
@RequiredArgsConstructor
@Tag(name = "Accountant - client team and sign-off")
public class AccountantAccessController {

    private final ClientAssignmentService assignments;
    private final EngagementService engagements;

    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('ACCOUNTANT_READ')")
    @Operation(summary = "Who works on this client")
    public ResponseEntity<ApiResponse<List<ClientAssignmentResponse>>> assignments(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success("Success", assignments.forClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAuthority('ACCOUNTANT_ASSIGN')")
    @Operation(summary = "Assign a person to this client as preparer, reviewer or partner")
    public ResponseEntity<ApiResponse<ClientAssignmentResponse>> assign(@PathVariable UUID clientId, @RequestBody AssignClientRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Assigned",
                assignments.assign(TenantContext.getTenantIdAsObject(), clientId, req.userId(), req.role(), TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @PreAuthorize("hasAuthority('ACCOUNTANT_ASSIGN')")
    @Operation(summary = "Take a person off this client")
    public ResponseEntity<ApiResponse<Void>> unassign(@PathVariable UUID clientId, @PathVariable UUID assignmentId) {
        assignments.unassign(TenantContext.getTenantIdAsObject(), clientId, assignmentId);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }

    @GetMapping("/engagements")
    @PreAuthorize("hasAuthority('ACCOUNTANT_READ')")
    @Operation(summary = "The client's financial years and where each stands")
    public ResponseEntity<ApiResponse<List<EngagementResponse>>> engagements(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success("Success", engagements.list(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/engagements/{year}/prepare")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    @Operation(summary = "Mark the year prepared (every journal posted)")
    public ResponseEntity<ApiResponse<EngagementResponse>> prepare(@PathVariable UUID clientId, @PathVariable int year,
                                                                  @RequestBody(required = false) EngagementActionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Marked prepared", engagements.prepare(TenantContext.getTenantIdAsObject(), clientId, year,
                req != null ? req.note() : null, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/engagements/{year}/review")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    @Operation(summary = "Review the prepared year (not by the person who prepared it)")
    public ResponseEntity<ApiResponse<EngagementResponse>> review(@PathVariable UUID clientId, @PathVariable int year,
                                                                 @RequestBody(required = false) EngagementActionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Marked reviewed", engagements.review(TenantContext.getTenantIdAsObject(), clientId, year,
                req != null ? req.note() : null, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/engagements/{year}/sign-off")
    @PreAuthorize("hasAuthority('ACCOUNTANT_SIGNOFF')")
    @Operation(summary = "Sign off the reviewed year; it then takes no more journals")
    public ResponseEntity<ApiResponse<EngagementResponse>> signOff(@PathVariable UUID clientId, @PathVariable int year,
                                                                  @RequestBody(required = false) EngagementActionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Signed off", engagements.signOff(TenantContext.getTenantIdAsObject(), clientId, year,
                req != null ? req.note() : null, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/engagements/{year}/reopen")
    @PreAuthorize("hasAuthority('ACCOUNTANT_WRITE')")
    @Operation(summary = "Send a prepared or reviewed year back, with the reason")
    public ResponseEntity<ApiResponse<EngagementResponse>> sendBack(@PathVariable UUID clientId, @PathVariable int year,
                                                                   @RequestBody EngagementActionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Year sent back", engagements.reopenOrSendBack(TenantContext.getTenantIdAsObject(),
                clientId, year, req.note(), TenantContext.getCurrentUserId(), false)));
    }

    @PostMapping("/engagements/{year}/reopen-signed-off")
    @PreAuthorize("hasAuthority('ACCOUNTANT_SIGNOFF')")
    @Operation(summary = "Reopen a signed-off year, with the reason")
    public ResponseEntity<ApiResponse<EngagementResponse>> reopenSigned(@PathVariable UUID clientId, @PathVariable int year,
                                                                       @RequestBody EngagementActionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Year reopened", engagements.reopenOrSendBack(TenantContext.getTenantIdAsObject(),
                clientId, year, req.note(), TenantContext.getCurrentUserId(), true)));
    }
}
