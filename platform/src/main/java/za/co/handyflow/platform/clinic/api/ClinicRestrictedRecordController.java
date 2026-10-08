package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicRestrictedRecordService;
import za.co.handyflow.platform.clinic.application.internal.RestrictedRecordRules;
import za.co.handyflow.platform.clinic.dto.RestrictedRecordDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** Restricted records and break-glass (CLINIC-DEC-008, 009). Enforcement is in ClinicRestrictedRecordConfig; this manages and reviews. */
@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
@Tag(name = "Clinic restricted records", description = "Restrict a record, break the glass, review the alerts")
public class ClinicRestrictedRecordController {

    private final ClinicRestrictedRecordService service;

    private static UUID tenant() { return TenantContext.getTenantIdAsObject().getValue(); }
    private static UUID user() { try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; } }

    @GetMapping("/patients/{patientId}/restriction")
    @PreAuthorize("hasAuthority('CLINIC_PATIENT_READ')")
    @Operation(summary = "Is this record restricted, and can the signed-in user open it now?")
    public ResponseEntity<ApiResponse<Status>> status(@PathVariable UUID patientId, Authentication auth) {
        boolean standing = auth != null && auth.getAuthorities().stream().anyMatch(a -> "CLINIC_RESTRICTED_RECORD_ACCESS".equals(a.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success("Success", service.status(tenant(), patientId, user(), standing)));
    }

    @PutMapping("/patients/{patientId}/restriction")
    @PreAuthorize("hasAuthority('CLINIC_RESTRICTED_RECORD_MANAGE')")
    @Operation(summary = "Restrict a patient's clinical record (category and reason required)")
    public ResponseEntity<ApiResponse<Status>> flag(@PathVariable UUID patientId, @RequestBody FlagRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Record restricted",
                service.flag(tenant(), patientId, user(), body == null ? null : body.category(), body == null ? null : body.reason())));
    }

    @DeleteMapping("/patients/{patientId}/restriction")
    @PreAuthorize("hasAuthority('CLINIC_RESTRICTED_RECORD_MANAGE')")
    @Operation(summary = "Lift a restriction (reason required, kept)")
    public ResponseEntity<ApiResponse<Status>> release(@PathVariable UUID patientId, @RequestBody ReasonRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Restriction lifted",
                service.release(tenant(), patientId, user(), body == null ? null : body.reason())));
    }

    @PostMapping("/patients/{patientId}/break-glass")
    @PreAuthorize("hasAuthority('CLINIC_BREAK_GLASS_VIEW')")
    @Operation(summary = "Break the glass: open a restricted record for a limited time with a typed reason. The practice is alerted.")
    public ResponseEntity<ApiResponse<Session>> start(@PathVariable UUID patientId, @RequestBody ReasonRequest body, HttpServletRequest request) {
        UUID u = user();
        if (u == null) throw new IllegalStateException("Sign in again before breaking the glass.");
        return ResponseEntity.ok(ApiResponse.success("Record opened",
                service.start(tenant(), patientId, u, body == null ? null : body.reason(), request.getRemoteAddr(),
                        request.getHeader("User-Agent"), null)));
    }

    @PostMapping("/patients/{patientId}/break-glass/print")
    @PreAuthorize("hasAuthority('CLINIC_BREAK_GLASS_PRINT')")
    @Operation(summary = "Record that a document from a restricted record was printed (the browser reports it)")
    public ResponseEntity<ApiResponse<Void>> print(@PathVariable UUID patientId, @RequestBody(required = false) PrintRequest body) {
        UUID u = user();
        UUID session = u == null ? null : service.activeSession(tenant(), patientId, u)
                .orElseThrow(() -> new IllegalStateException("There is no open break-glass session for this record.")) ;
        service.audit(tenant(), session, patientId, u, RestrictedRecordRules.PRINTED, "DOCUMENT", null, body == null ? null : body.document());
        return ResponseEntity.ok(ApiResponse.success("Recorded", null));
    }

    @GetMapping("/break-glass/sessions")
    @PreAuthorize("hasAuthority('CLINIC_BREAK_GLASS_REVIEW')")
    @Operation(summary = "Break-glass sessions for review, newest first")
    public ResponseEntity<ApiResponse<List<SessionRow>>> sessions(@RequestParam(defaultValue = "true") boolean unacknowledged,
                                                                  @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.sessions(tenant(), unacknowledged, limit)));
    }

    @PostMapping("/break-glass/sessions/{sessionId}/acknowledge")
    @PreAuthorize("hasAuthority('CLINIC_BREAK_GLASS_REVIEW')")
    @Operation(summary = "Acknowledge a break-glass session after review")
    public ResponseEntity<ApiResponse<Void>> acknowledge(@PathVariable UUID sessionId, @RequestBody(required = false) AcknowledgeRequest body) {
        service.acknowledge(tenant(), sessionId, user(), body == null ? null : body.note());
        return ResponseEntity.ok(ApiResponse.success("Acknowledged", null));
    }
}
