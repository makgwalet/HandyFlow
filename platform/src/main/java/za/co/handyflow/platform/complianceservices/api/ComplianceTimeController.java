package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.ComplianceTimeService;
import za.co.handyflow.platform.complianceservices.dto.TimeDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Time", description = "Hours worked for clients")
public class ComplianceTimeController {

    private static final String READ = "hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ComplianceTimeService service;
    private final FeatureGuard featureGuard;

    @PostMapping("/time-entries")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EntryView>> record(@Valid @RequestBody RecordRequest body, Authentication auth) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.record(TenantContext.getTenantIdAsObject(), body, TenantContext.getCurrentUserId(), admin(auth), LocalDate.now(SAST))));
    }

    @GetMapping("/time-entries/mine")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<List<EntryView>>> mine(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("complianceservices");
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        return ResponseEntity.ok(ApiResponse.success(service.mine(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), from != null ? from : end.minusDays(13), end)));
    }

    @GetMapping("/time-entries/summary")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Summary>> summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("complianceservices");
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        return ResponseEntity.ok(ApiResponse.success(service.summary(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), from != null ? from : end.minusDays(6), end)));
    }

    @GetMapping("/clients/{clientId}/time-entries")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<EntryView>>> forClient(@PathVariable UUID clientId,
                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("complianceservices");
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        return ResponseEntity.ok(ApiResponse.success(service.forClient(TenantContext.getTenantIdAsObject(), clientId, from != null ? from : end.minusDays(89), end)));
    }

    @PostMapping("/time-entries/{id}/void")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EntryView>> voidEntry(@PathVariable UUID id, @Valid @RequestBody VoidRequest body, Authentication auth) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.voidEntry(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), admin(auth), body.reason())));
    }

    private static boolean admin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "COMPLIANCE_SERVICES_ADMIN".equals(a.getAuthority()));
    }
}
