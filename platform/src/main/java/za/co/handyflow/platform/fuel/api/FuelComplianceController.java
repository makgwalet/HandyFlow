package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelComplianceEvidenceFileService;
import za.co.handyflow.platform.fuel.application.internal.FuelComplianceService;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.StoredFile;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import za.co.handyflow.platform.fuel.dto.ComplianceLinkageDtos.*;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos.ReasonRequest;
import za.co.handyflow.platform.fuel.dto.ComplianceRequest;
import za.co.handyflow.platform.fuel.dto.ComplianceResponse;
import za.co.handyflow.platform.fuel.dto.RenewRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Stage 4B: compliance register. */
@RestController
@RequestMapping("/api/v1/fuel/compliance")
@RequiredArgsConstructor
@Tag(name = "Fuel Compliance", description = "Licences, calibrations, inspections and certificates")
public class FuelComplianceController {

    private final FuelComplianceService compliance;
    private final FuelComplianceEvidenceFileService evidenceFiles;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<ComplianceResponse>>> list() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(compliance.list(TenantContext.getTenantIdAsObject(), FuelComplianceAuthority.canSeeDetail())));
    }

    @GetMapping("/coverage")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Coverage>> coverage() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(compliance.coverage(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_COMPLIANCE_MANAGE','FUEL_COMPLIANCE_VERIFY')")
    public ResponseEntity<ApiResponse<List<EvidenceView>>> evidence(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(compliance.listEvidence(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_COMPLIANCE_MANAGE')")
    public ResponseEntity<ApiResponse<EvidenceView>> addEvidence(@PathVariable UUID id, @Valid @RequestBody EvidenceRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Added", compliance.addEvidence(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping(value = "/{id}/evidence/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_COMPLIANCE_MANAGE')")
    public ResponseEntity<ApiResponse<EvidenceView>> addEvidenceFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                                                    @RequestParam(value = "note", required = false) String note) {
        featureGuard.requireModule("fuel");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new IllegalArgumentException("The file could not be read."); }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Added",
                evidenceFiles.add(TenantContext.getTenantIdAsObject(), id, file.getOriginalFilename(), bytes, note, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/evidence/{evidenceId}/file")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_COMPLIANCE_MANAGE','FUEL_COMPLIANCE_VERIFY')")
    public ResponseEntity<byte[]> evidenceFile(@PathVariable UUID evidenceId) {
        featureGuard.requireModule("fuel");
        StoredFile f = evidenceFiles.download(TenantContext.getTenantIdAsObject(), evidenceId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.filename()).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(f.bytes());
    }

    @PostMapping("/evidence/{evidenceId}/verify")
    @PreAuthorize("hasAuthority('FUEL_COMPLIANCE_VERIFY')")
    public ResponseEntity<ApiResponse<EvidenceView>> verifyEvidence(@PathVariable UUID evidenceId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Verified", compliance.verifyEvidence(TenantContext.getTenantIdAsObject(), evidenceId, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/evidence/{evidenceId}/supersede")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_COMPLIANCE_MANAGE')")
    public ResponseEntity<ApiResponse<EvidenceView>> supersedeEvidence(@PathVariable UUID evidenceId, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Replaced", compliance.supersedeEvidence(TenantContext.getTenantIdAsObject(), evidenceId, req.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_COMPLIANCE_MANAGE')")
    public ResponseEntity<ApiResponse<ComplianceResponse>> create(@Valid @RequestBody ComplianceRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Added", compliance.create(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_COMPLIANCE_MANAGE')")
    public ResponseEntity<ApiResponse<ComplianceResponse>> update(@PathVariable UUID id, @Valid @RequestBody ComplianceRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Updated", compliance.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_COMPLIANCE_MANAGE')")
    public ResponseEntity<ApiResponse<ComplianceResponse>> renew(@PathVariable UUID id, @Valid @RequestBody RenewRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Renewed", compliance.renew(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_COMPLIANCE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        compliance.remove(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }
}
