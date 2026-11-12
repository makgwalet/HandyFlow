package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelEvidenceFileService;
import za.co.handyflow.platform.fuel.application.internal.FuelEvidenceService;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.StoredFile;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.Upload;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import za.co.handyflow.platform.fuel.application.internal.FuelVarianceService;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Phase 3: delivery variance review and proof-of-delivery evidence. */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel Distribution Controls", description = "Variance review and proof-of-delivery evidence")
public class FuelDistributionController {

    private final FuelVarianceService variances;
    private final FuelEvidenceService evidence;
    private final FuelEvidenceFileService evidenceFiles;
    private final FeatureGuard featureGuard;

    @GetMapping("/variances")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_VARIANCE_REVIEW')")
    public ResponseEntity<ApiResponse<VarianceList>> variances() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(variances.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/variances/{id}/investigate")
    @PreAuthorize("hasAuthority('FUEL_VARIANCE_REVIEW')")
    public ResponseEntity<ApiResponse<VarianceView>> investigate(@PathVariable UUID id, @Valid @RequestBody NoteRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Marked as investigating",
                variances.investigate(TenantContext.getTenantIdAsObject(), id, req.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/variances/{id}/close")
    @PreAuthorize("hasAuthority('FUEL_VARIANCE_REVIEW')")
    public ResponseEntity<ApiResponse<VarianceView>> close(@PathVariable UUID id, @Valid @RequestBody CloseVarianceRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Variance closed",
                variances.close(TenantContext.getTenantIdAsObject(), id, req.decision(), req.note(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/deliveries/{id}/evidence")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<EvidenceView>>> evidence(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(evidence.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/deliveries/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_DISTRIBUTION_MANAGE')")
    public ResponseEntity<ApiResponse<EvidenceView>> addEvidence(@PathVariable UUID id, @Valid @RequestBody EvidenceRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence added",
                evidence.addAsOffice(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/driver/loads/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_DISTRIBUTION_MANAGE')")
    public ResponseEntity<ApiResponse<List<EvidenceView>>> myEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(evidence.listAsDriver(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id)));
    }

    @PostMapping("/driver/loads/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_DISTRIBUTION_MANAGE')")
    public ResponseEntity<ApiResponse<EvidenceView>> addMyEvidence(@PathVariable UUID id, @Valid @RequestBody EvidenceRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence added",
                evidence.addAsDriver(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req)));
    }

    @PostMapping(value = "/deliveries/{id}/evidence/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_DISTRIBUTION_MANAGE')")
    public ResponseEntity<ApiResponse<EvidenceView>> addEvidenceFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file, @RequestParam("kind") String kind,
                                                                    @RequestParam(value = "note", required = false) String note, @RequestParam(value = "clientRequestId", required = false) UUID clientRequestId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence added",
                evidenceFiles.addAsOffice(TenantContext.getTenantIdAsObject(), id, upload(file, kind, note, clientRequestId), TenantContext.getCurrentUserId())));
    }

    @PostMapping(value = "/driver/loads/{id}/evidence/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_DISTRIBUTION_MANAGE')")
    public ResponseEntity<ApiResponse<EvidenceView>> addMyEvidenceFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file, @RequestParam("kind") String kind,
                                                                      @RequestParam(value = "note", required = false) String note, @RequestParam(value = "clientRequestId", required = false) UUID clientRequestId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence added",
                evidenceFiles.addAsDriver(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, upload(file, kind, note, clientRequestId))));
    }

    @GetMapping("/evidence/{id}/file")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<byte[]> evidenceFile(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return served(evidenceFiles.download(TenantContext.getTenantIdAsObject(), id));
    }

    @GetMapping("/driver/evidence/{id}/file")
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_DISTRIBUTION_MANAGE')")
    public ResponseEntity<byte[]> myEvidenceFile(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return served(evidenceFiles.downloadAsDriver(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id));
    }

    private static Upload upload(MultipartFile file, String kind, String note, UUID clientRequestId) {
        try { return new Upload(kind, file.getOriginalFilename(), file.getBytes(), note, clientRequestId); }
        catch (IOException e) { throw new IllegalArgumentException("The file could not be read."); }
    }

    /** Served as an attachment-safe, never-sniffed, never-cached response: proof of delivery is private to the company. */
    private static ResponseEntity<byte[]> served(StoredFile f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.filename()).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(f.bytes());
    }

    @PostMapping("/evidence/{id}/supersede")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_DISTRIBUTION_MANAGE')")
    public ResponseEntity<ApiResponse<List<EvidenceView>>> supersede(@PathVariable UUID id, @Valid @RequestBody SupersedeRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Evidence replaced",
                evidence.supersede(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/evidence/{id}/verify")
    @PreAuthorize("hasAuthority('FUEL_EVIDENCE_VERIFY')")
    public ResponseEntity<ApiResponse<EvidenceView>> verify(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Evidence verified and locked",
                evidence.verify(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }
}
