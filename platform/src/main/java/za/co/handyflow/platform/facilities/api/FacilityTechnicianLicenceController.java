package za.co.handyflow.platform.facilities.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.facilities.application.internal.FacilityTechnicianLicenceService;
import za.co.handyflow.platform.facilities.dto.ReasonRequest;
import za.co.handyflow.platform.facilities.dto.TechnicianLicenceRequest;
import za.co.handyflow.platform.facilities.dto.TechnicianLicenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
@Tag(name = "Facilities - Technician licences", description = "Trade licences and tickets held by technicians")
public class FacilityTechnicianLicenceController {

    private static final String CAN_READ = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_COMPLIANCE_MANAGE')";
    private static final String CAN_MANAGE = "hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN','FACILITIES_COMPLIANCE_MANAGE')";

    private final FacilityTechnicianLicenceService service;
    private final FeatureGuard featureGuard;
    private final EvidenceFacade evidenceFacade;

    @GetMapping("/technicians/{id}/licences")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<TechnicianLicenceResponse>>> list(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/technicians/{id}/licences")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<TechnicianLicenceResponse>> add(@PathVariable UUID id, @Valid @RequestBody TechnicianLicenceRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Licence added",
                service.add(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/technicians/{id}/licences/{licenceId}/revoke")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<TechnicianLicenceResponse>> revoke(
            @PathVariable UUID id, @PathVariable UUID licenceId, @Valid @RequestBody ReasonRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Licence revoked",
                service.revoke(TenantContext.getTenantIdAsObject(), id, licenceId, request.reason())));
    }

    @GetMapping("/technician-licences/attention")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Licences that have lapsed or expire within withinDays (default 30), across all active technicians")
    public ResponseEntity<ApiResponse<List<TechnicianLicenceResponse>>> attention(@RequestParam(required = false) Integer withinDays) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(service.needingAttention(TenantContext.getTenantIdAsObject(), withinDays)));
    }

    @PostMapping("/technicians/{id}/licences/{licenceId}/evidence")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Attach a scan or photo of the licence")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @PathVariable UUID licenceId, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "DOCUMENT") String evidenceType) {
        featureGuard.requireModule("facilities");
        service.requireExists(TenantContext.getTenantIdAsObject(), id, licenceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                evidenceFacade.attach(TenantContext.getTenantIdAsObject(), file, evidenceType, "facilities",
                        "FacilityTechnicianLicence", licenceId, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/technicians/{id}/licences/{licenceId}/evidence")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidence(@PathVariable UUID id, @PathVariable UUID licenceId) {
        featureGuard.requireModule("facilities");
        service.requireExists(TenantContext.getTenantIdAsObject(), id, licenceId);
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), "facilities", "FacilityTechnicianLicence", licenceId)));
    }
}
